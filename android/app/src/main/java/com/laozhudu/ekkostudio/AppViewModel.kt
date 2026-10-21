package com.laozhudu.ekkostudio

import android.app.Application
import android.app.DownloadManager
import android.net.Uri
import android.media.MediaPlayer
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen {
    Loading, Onboarding, Login, Chats, Groups, AgentHub, Conversation, Room, Profiles,
    Settings, MoreSettings, SettingsGroup, Channels, Channel, CronJobs, CronJob, CronHistory,
    Kanban, KanbanTask, Skills, Skill, Plugins, Mcp, Pets, Insights, AgentRuntimes, Workflows, GlobalAgent, EkkoHub, Files, Logs, Connections, Journey, Webhooks, RuntimeVersions, Appearance, Terminal, WriteGate, ModelSettings, StudioUpdate, Workspace, Pricing, ApiKeys, RoomSettings, RoomFiles, WorkflowRun,
    /** The official `pages/devices` screen: saved Studios, pairing, account menu. */
    Devices,
}

/** The official `ApiRouteSwitch` options. Only [LAN] is reachable in this build. */
object ApiRoute {
    /** Direct connection to a Studio on this network — the app's whole point. */
    const val LAN = "lan"

    /**
     * The vendor's cloud relay (`cloudflare` / `official` upstream). The official
     * UI offers it; this build deliberately cannot select it, because there is no
     * cloud account to relay through and pretending otherwise would be a lie in
     * the UI. Kept as a constant so the disabled option is a real, named option.
     */
    const val CLOUD = "cloud"
}

/** Settings is a short list of these; each opens its own screen. */
enum class SettingsGroup {
    Account, Server, Users, Profile, Models, Agent, Memory, Compression, Sessions,
    Privacy, Proxy, Display, Device, About,
}

/** The app's three root destinations. Agent tools live outside Settings. */
enum class Tab { Chats, Groups, Agent }

private fun Screen.isRootDestination() = this in setOf(Screen.Chats, Screen.Groups, Screen.AgentHub, Screen.Login, Screen.Onboarding)
private fun Screen.isTransientDestination() = this == Screen.Loading

private data class SessionBootstrap(
    val user: CurrentUser,
    val profiles: List<Profile>,
    val sessions: List<SessionSummary>,
)

private data class AccountSettingsData(
    val user: CurrentUser,
    val locks: List<LockedIp>,
    val avatar: AvatarSpec,
)

data class ChatLine(
    val text: String,
    val fromUser: Boolean,
    val isError: Boolean = false,
    val timestamp: String? = null,
    val sender: String? = null,
    /** What the model thought on the way to this answer, when it reports it. */
    val reasoning: String? = null,
    /** True while the words are still arriving. */
    val streaming: Boolean = false,
    /** Tool calls reported by Studio while this reply is being produced. */
    val tools: List<ChatToolStep> = emptyList(),
    /** Local timing keeps the live Thinking counter moving between events. */
    val startedAtMillis: Long? = null,
    val finishedAtMillis: Long? = null,
    val messageId: String? = null,
    /** Text this message answers, rendered as a quote block inside the bubble. */
    val replyToNote: String? = null,
)

data class ChatToolStep(
    val id: String,
    val name: String,
    val detail: String?,
    val status: ToolRunStatus,
    val startedAtMillis: Long,
    val durationSeconds: Double? = null,
)

data class PendingRunAction(
    val kind: RequiredAction,
    val id: String,
    val prompt: String,
    val options: List<String>,
    val sessionId: String,
)

data class UiState(
    val screen: Screen = Screen.Login,
    val tab: Tab = Tab.Chats,
    val baseUrl: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val account: String? = null,
    val currentUser: CurrentUser? = null,
    val profiles: List<Profile> = emptyList(),
    /** Blank means "All profiles", the same default Studio shows. */
    val profileFilter: String = "",
    val activeProfile: String = "",
    val sessions: List<SessionSummary> = emptyList(),
    /** Drives both the toolbar refresh and the pull-to-refresh indicator. */
    val refreshingSessions: Boolean = false,
    val rooms: List<Room> = emptyList(),
    val openSession: SessionSummary? = null,
    val openRoom: RoomDetail? = null,
    /** Agent presets are account-wide, so they load once and are reused per room. */
    val agentPresets: List<AgentPreset> = emptyList(),
    val presetsLoaded: Boolean = false,
    /** Set while a group agent add/edit/delete is in flight. */
    val roomAgentBusy: String? = null,
    val roomSaving: Boolean = false,
    /** Room workspace browser. */
    val roomFiles: RoomWorkspaceListing = RoomWorkspaceListing("", "", emptyList()),
    val roomFilesPath: String = "",
    val roomFilesLoading: Boolean = false,
    val roomFileOpen: RoomWorkspaceEntry? = null,
    val roomFileContent: String = "",
    val roomFileSaving: Boolean = false,
    /** Remote agent connectors and the pending requests to join this room. */
    val groupLink: GroupLinkCapabilities? = null,
    val groupConnections: List<GroupLinkConnection> = emptyList(),
    val agentLinkRequests: List<AgentLinkRequest> = emptyList(),
    val lines: List<ChatLine> = emptyList(),
    val loadingHistory: Boolean = false,
    val sending: Boolean = false,
    val attachments: List<Upload> = emptyList(),
    val attaching: Boolean = false,
    val recording: Boolean = false,
    val transcribing: Boolean = false,
    /** Text produced by the last recording, consumed by the composer. */
    val transcript: String? = null,
    /** The next submitted draft came from STT and should receive a spoken reply. */
    val voiceReplyPending: Boolean = false,
    val speaking: Boolean = false,
    /**
     * Which message the speech player is on. The official client tracks this so
     * the button on *that* bubble turns into a pause icon; a single app-wide flag
     * made every bubble look like it was playing.
     */
    val speakingMessageId: String? = null,
    val models: List<ModelOption> = emptyList(),
    /** Profile whose entries are currently in [models]. */
    val modelsProfile: String? = null,
    val loadingModels: Boolean = false,
    /**
     * Last model-list failure. Kept separate from [error] because the picker
     * renders as a bottom sheet above the screen that owns [error]; without it
     * a failed load looks like a permanently empty sheet with no retry.
     */
    val modelsError: String? = null,
    /** Blank means the profile default, matching Studio's "Default" chip. */
    val reasoningEffort: String = "",
    /** BCP-47 tag chosen in Settings; blank follows the system. */
    val language: String = "",
    /** system, light, or dark. */
    val appearance: String = "dark",
    /** Saved Studio deployments, newest first. The active one is [baseUrl]. */
    val instances: List<StudioInstance> = emptyList(),
    /** Official `pages/devices` runtime state — see [DevicesUi]. */
    val devicesUi: DevicesUi = DevicesUi(),
    /** .remember-check: an account and password kept on this device for the login form. */
    val rememberCredentials: Boolean = false,
    val savedUsername: String = "",
    val savedPassword: String = "",
    val sessionModel: String? = null,
    val sessionProvider: String? = null,
    /** Prompt tokens the provider last reported; -1 = not known yet. */
    val contextTokens: Long = -1,
    val contextWindow: Long = 0,
    val loadingContext: Boolean = false,
    val defaultModel: String? = null,
    val savingSetting: Boolean = false,
    /** The tool the agent is running right now, when it says so. */
    val activity: String? = null,
    /** running | completed | failed, straight from `session.activity`. */
    val runStatus: String = "",
    /** Latest lifecycle notices from `agent.event` (retries, warnings). */
    val runNotices: List<String> = emptyList(),
    /** When the current run started, for the elapsed counter. */
    val runStartedAtMillis: Long = 0,
    /** Session that owns the in-flight run; null when nothing is running. */
    val runSessionId: String? = null,
    /** True once the room socket is carrying messages. */
    val roomLive: Boolean = false,
    val serverConfig: ServerConfig? = null,
    /** The channel whose settings are open, if any. */
    val openChannel: String? = null,
    val weixinQr: WeixinQrUi = WeixinQrUi(),
    val openGroup: SettingsGroup? = null,
    /** Parent hub for a settings group, channel list, or scheduled-jobs list. */
    val toolReturnScreen: Screen = Screen.Settings,
    /** Exact screen that opened Profiles; it is shared by several root surfaces. */
    val profilesReturnScreen: Screen = Screen.Chats,
    val agentSettings: AgentSettings? = null,
    val autoStart: AutoStartPolicy? = null,
    val loadingAgentSettings: Boolean = false,
    val studioSettings: StudioSettings? = null,
    val loadingStudioSettings: Boolean = false,
    val lockedIps: List<LockedIp> = emptyList(),
    val accountAvatar: AvatarSpec? = null,
    val loadingAccountSettings: Boolean = false,
    val managedUsers: List<ManagedUser> = emptyList(),
    val managedProfiles: List<String> = emptyList(),
    val loadingManagedUsers: Boolean = false,
    val modelProviders: List<ModelProvider> = emptyList(),
    val loadingModelProviders: Boolean = false,
    val cronJobs: List<CronJob> = emptyList(),
    val writeGateRecords: List<WriteGateRecord> = emptyList(),
    val writeGateLoading: Boolean = false,
    val writeGateBusyId: String? = null,
    val modelCatalog: ModelCatalog? = null,
    val modelsLoading: Boolean = false,
    val selectedModel: String = "",
    val contextLimit: String = "",
    val studioPreview: StudioUpdatePreview? = null,
    val studioTags: List<StudioUpdateTag> = emptyList(),
    val studioLoading: Boolean = false,
    val serverVersions: ServerVersions? = null,
    val remoteVersions: RemoteVersions? = null,
    /** How Hermes Agent itself is installed on the server (git / npm / docker / nix). */
    val hermesDeploy: String = "",
    val hermesVersion: String = "",
    val hermesUpdateLog: String = "",
    val hermesUpdating: Boolean = false,
    val serverRestarting: Boolean = false,
    /** Workspace browser: path is relative to the profile root the API uses. */
    val workspacePath: String = "",
    val workspaceEntries: List<WorkspaceEntry> = emptyList(),
    val workspaceLoading: Boolean = false,
    val workspaceFile: String? = null,
    val workspaceFileName: String = "",
    /** History paging: newest-first offsets, as the server defines them. */
    val historyTotal: Int = 0,
    val historyHasMore: Boolean = false,
    val historyOffset: Int = 0,
    val loadingOlder: Boolean = false,
    /** True once an update finished and the server still has to be restarted. */
    val restartRequired: Boolean = false,
    /** 0..100, or -1 when the update reports no progress of its own. */
    val updateProgress: Int = -1,
    val agentPolicies: List<AgentPolicy> = emptyList(),
    /** Server whose live tool inventory is open in a dialog, plus the data. */
    val mcpToolsFor: String? = null,
    val mcpLiveTools: List<McpServerTools> = emptyList(),
    val mcpToolsLoading: Boolean = false,
    /** Saved workspace folders offered as shortcuts. */
    val workspaceDirectories: List<WorkspaceDirectory> = emptyList(),
    /** Skill bundles, shown in the slash-command sheet. */
    val bundles: List<SkillBundle> = emptyList(),
    val bundlesLoading: Boolean = false,
    val textScale: Float = 1f,
    /** Bumped by the "check for updates" action to trigger a manual check. */
    val appUpdateCheckTick: Int = 0,
    val taskPlans: List<TaskPlanSnapshot> = emptyList(),
    val cronLoading: Boolean = false,
    /** The job currently running a pause/resume/run/delete request. */
    val cronActionId: String? = null,
    val editingCronJob: CronJob? = null,
    val cronEditorJobId: String? = null,
    val cronEditorLoading: Boolean = false,
    val cronSkills: List<String> = emptyList(),
    val cronDeliveryTargets: List<CronDeliveryTarget> = emptyList(),
    val cronHistoryJob: CronJob? = null,
    val cronRuns: List<CronRun> = emptyList(),
    val cronHistoryLoading: Boolean = false,
    val cronRunLoading: Boolean = false,
    val openCronRun: CronRunDetail? = null,
    val kanban: KanbanUiState = KanbanUiState(),
    val skillsUi: SkillsUiState = SkillsUiState(),
    val pluginsUi: PluginsUiState = PluginsUiState(),
    val mcpUi: McpUiState = McpUiState(),
    val petsUi: PetsUiState = PetsUiState(),
    val usageStats: UsageStats? = null,
    /** Null means "all accounts"; otherwise the server-side filter id. */
    val usageAccount: String? = null,
    val usageDays: Int = 30,
    val runtimePerformance: RuntimePerformance? = null,
    val loadingInsights: Boolean = false,
    val notice: String? = null,
    val agentRuntimes: List<AgentRuntimeStatus> = emptyList(),
    val loadingAgentRuntimes: Boolean = false,
    val selectedRuntime: AgentRuntimeSelection = AgentRuntimeSelection(),
    val pendingRunAction: PendingRunAction? = null,
    val queuedRuns: List<QueuedRun> = emptyList(),
    val queueInsertionActive: Boolean = false,
    val backgroundAgentRuns: List<BackgroundAgentRun> = emptyList(),
    val workspaceRunChanges: List<WorkspaceRunChange> = emptyList(),
    val sessionCategories: List<SessionCategory> = emptyList(),
    val sessionSearchResults: List<SessionSummary>? = null,
    val sessionLimit: Int = 80,
    val workflows: List<StudioWorkflow> = emptyList(),
    val workflowRuns: Map<String, List<StudioWorkflowRun>> = emptyMap(),
    /** The run whose detail screen is open, and its id so it can be reloaded. */
    val openWorkflowRun: StudioWorkflowRun? = null,
    val openWorkflowRunId: String? = null,
    val workflowSchedules: Map<String, List<WorkflowSchedule>> = emptyMap(),
    val loadingWorkflows: Boolean = false,
    val ekkoMemories: List<EkkoMemory> = emptyList(),
    val ekkoSkills: List<SkillCategory> = emptyList(),
    val ekkoMcpServers: List<EkkoMcpServer> = emptyList(),
    val loadingEkko: Boolean = false,
    val profileRuntimeStatuses: Map<String, String> = emptyMap(),
    val filesPath: String = "",
    val studioFiles: List<StudioFile> = emptyList(),
    val openFile: StudioFile? = null,
    val openFileContent: String = "",
    val studioLogs: List<StudioLogFile> = emptyList(),
    val openLog: StudioLogFile? = null,
    val logEntries: List<StudioLogEntry> = emptyList(),
    val appRelay: AppRelayStatus? = null,
    val connectionsLoading: Boolean = false,
    val appConnections: List<AppConnection> = emptyList(),
    val appAuthorization: AppAuthorization? = null,
    val ekkoExternalDirectories: List<String> = emptyList(),
    val ekkoOpenSkill: SkillInfo? = null,
    val ekkoSkillContent: String = "",
    val ekkoSkillFiles: List<String> = emptyList(),
    val ekkoSkillFilePreviewPath: String? = null,
    val ekkoSkillFilePreviewContent: String = "",
    val journey: JourneyGraph? = null,
    val skillUsage: SkillUsage? = null,
    val webhooks: List<WebhookEndpoint> = emptyList(),
    val webhookEvents: List<String> = emptyList(),
    val runtimeVersions: RuntimeVersions? = null,
    /** Sessions the server reports as running right now, including remote ones. */
    val remoteRunningSessions: Set<String> = emptySet(),
    /** Live compaction figures, so the strip can say what it actually did. */
    val compression: CompressionUi? = null,
    val runtimeJobs: List<RuntimeJob> = emptyList(),
    /** Model pricing table, edited locally then PUT as a whole. */
    val pricingRates: List<PricingRate> = emptyList(),
    val pricingLoaded: Boolean = false,
    val pricingDirty: Boolean = false,
    val pricingSaving: Boolean = false,
    val modelApiKeys: List<ModelApiKey> = emptyList(),
    val modelApiKeysLoaded: Boolean = false,
    val modelApiCatalog: List<KeyModel> = emptyList(),
    /** Set right after creation; the server keeps only a hash. */
    val createdApiKeySecret: String? = null,
    /** Opened by tapping the context readout; null when the sheet is closed. */
    val sessionUsage: SessionUsage? = null,
    /** Chosen in the new-chat sheet, applied to the run that creates the session. */
    val newChatCategoryId: Int? = null,
    val newChatWorkspace: String? = null,
    val newChatSheetOpen: Boolean = false,
    /** Set by a search hit; the conversation scrolls to that message once loaded. */
    val focusMessageId: String? = null,
    val themeSettings: ThemeSettings? = null,
    val kanbanDiagnostics: List<String> = emptyList(),
    val kanbanStats: String = "",
    val kanbanLog: String = "",
    val kanbanAttachments: List<String> = emptyList(),
)

/** Numbers from `compression.started|completed`; wording is the UI's job. */
data class CompressionUi(
    val running: Boolean,
    val messageCount: Int = 0,
    val beforeTokens: Long = 0,
    val afterTokens: Long = 0,
)

data class WeixinQrUi(
    val status: String = "idle",
    val id: String = "",
    val url: String = "",
)

/**
 * What the last reachability pass learned about one saved Studio.
 *
 * The official page gets this from the cloud's device-status endpoint; a direct
 * connection has no broker, so the equivalent is `GET /api/auth/me` against the
 * saved URL with its saved token — the same "cheap check that a stored token is
 * still valid" the sign-in path uses.
 */
data class DeviceProbe(
    val online: Boolean = false,
    /** Account name reported by the device, for the `.device-system` fallback. */
    val username: String = "",
    /** Active Studio web-client version, i.e. the official `hermes_web_ui_version`. */
    val webUiVersion: String = "",
    /** Server platform, i.e. the official `metadata.os`. */
    val platform: String = "",
    val checkedAt: Long = 0,
)

/**
 * Runtime state of the official `pages/devices` screen (scope `data-v-61a2f361`).
 *
 * [checking] is the official `ve(device)` predicate that renders the card's
 * 检测中 pill; [probes] is the per-device result behind 在线/离线 and the
 * "Studio &lt;version&gt;" cell.
 */
data class DevicesUi(
    val checking: Set<String> = emptySet(),
    val probes: Map<String, DeviceProbe> = emptyMap(),
    /** Official `fe(device)`: the card currently opening, i.e. 连接中. */
    val connecting: String? = null,
    /** Official `Ee(device)`: the card currently being removed, i.e. 删除中. */
    val deleting: String? = null,
    /** The pairing/rename panel's submit button is running. */
    val saving: Boolean = false,
    /** Official `ApiRouteSwitch` selection. Only [ApiRoute.LAN] can be chosen. */
    val route: String = ApiRoute.LAN,
) {
    fun probe(url: String): DeviceProbe? = probes[url]

    /** Official `ve(device)`: a probe for this device is in flight. */
    fun isChecking(url: String): Boolean = url in checking
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)

    /** Resolves strings in the language chosen in Settings, not the phone's. */
    private val localized = AppLocale.wrap(app)
    private val api = HermesApi(store.baseUrl, store.token)
    private val chat = ChatSocket(store.baseUrl, store.token)
    private val group = GroupSocket(store.baseUrl, store.token)
    private val recorder = Recorder(app)
    private var speechPlayer: MediaPlayer? = null
    private var runJob: kotlinx.coroutines.Job? = null
    /** Set when another device posts while this phone is elsewhere. */
    private var peerMessagePending = false
    private var historyJob: kotlinx.coroutines.Job? = null
    private var roomJob: kotlinx.coroutines.Job? = null
    private var roomLoadJob: kotlinx.coroutines.Job? = null
    private var weixinQrJob: Job? = null
    /** The official `pages/devices` reachability pass; restarted on every refresh. */
    private var probeJob: Job? = null
    private var openingRoomId: String? = null
    private var activeRunSessionId: String? = null
    private val resumePageIds = mutableMapOf<String, String>()
    private val queuedDownloadNames = mutableSetOf<String>()
    /** Actual visit order. Several Studio tools can be opened from more than one parent. */
    private val navigationHistory = ArrayDeque<Screen>()
    private var observedScreen: Screen? = null
    private var consumingBackNavigation = false

    private val _state = MutableStateFlow(
        UiState(
            // A configured install must never flash the credentials form: it reads
            // as "sign in again" even though the token is still good.
            screen = when {
                store.isConfigured -> Screen.Loading
                store.onboarded -> Screen.Login
                else -> Screen.Onboarding
            },
            baseUrl = store.baseUrl,
            reasoningEffort = store.reasoningEffort,
            language = store.language,
            appearance = store.appearance,
            textScale = store.textScale,
            instances = store.instances,
            devicesUi = DevicesUi(route = store.apiRoute),
            rememberCredentials = store.rememberCredentials,
            savedUsername = store.savedUsername,
            savedPassword = store.savedPassword,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            state.collect { snapshot ->
                val previous = observedScreen
                val current = snapshot.screen
                if (previous == null) {
                    observedScreen = current
                } else if (previous != current) {
                    if (consumingBackNavigation) {
                        consumingBackNavigation = false
                    } else if (current.isRootDestination()) {
                        navigationHistory.clear()
                    } else if (!previous.isTransientDestination()) {
                        if (navigationHistory.lastOrNull() != previous) navigationHistory.addLast(previous)
                    }
                    observedScreen = current
                }
            }
        }
        // The cached mark is on disk, so the launch screen can show it at once.
        viewModelScope.launch { AppLogo.load(app) }
        if (store.isConfigured) restoreSession()
    }

    fun finishOnboarding() {
        store.onboarded = true
        _state.update { it.copy(screen = Screen.Login) }
    }

    private fun restoreSession() = launchWork(
        work = {
            api.update(store.baseUrl, store.token)
            chat.update(store.baseUrl, store.token)
            group.update(store.baseUrl, store.token)
            val user = api.currentUser()
            SessionBootstrap(user, api.profiles(), api.sessions(null))
        },
        onSuccess = { (user, profiles, sessions) ->
            store.sessionsSnapshot = api.sessionsToJson(sessions)
            _state.update {
                it.copy(
                    screen = Screen.Chats,
                    account = user.username,
                    currentUser = user,
                    profiles = profiles,
                    activeProfile = pickProfile(profiles),
                    sessions = sessions,
                    error = null,
                )
            }
            syncBranding()
        },
        onFailure = { failure ->
            if (failure.invalidatesSavedSession()) {
                store.clearCredentials()
                _state.update {
                    it.copy(
                        screen = Screen.Login,
                        error = str(R.string.error_session_expired, failure.readableMessage(localized)),
                    )
                }
            } else {
                // A timeout or an offline server does not invalidate a token.
                // Fall back to the last known list so Chats opens offline instead
                // of parking the user on a blank launch screen.
                val cached = api.sessionsFromSnapshot(store.sessionsSnapshot)
                val message = str(R.string.error_session_restore, failure.readableMessage(localized))
                _state.update {
                    if (cached.isNotEmpty()) {
                        it.copy(screen = Screen.Chats, sessions = cached, error = message)
                    } else {
                        it.copy(screen = Screen.Loading, error = message)
                    }
                }
            }
        },
    )

    fun retrySession() {
        if (!store.isConfigured || _state.value.busy) return
        _state.update { it.copy(screen = Screen.Loading, error = null) }
        restoreSession()
    }

    /** True when the user has not asked to skip this release tag. */
    fun shouldPromptForUpdate(tag: String): Boolean =
        tag.isNotBlank() && tag != store.skippedUpdateTag

    fun skipUpdateTag(tag: String) {
        if (tag.isNotBlank()) store.skippedUpdateTag = tag
    }

    // ---- Terminal ------------------------------------------------------

    private var terminal: TerminalClient? = null

    private val _terminalOutput = MutableStateFlow("")
    val terminalOutput: StateFlow<String> = _terminalOutput.asStateFlow()

    private val _terminalConnected = MutableStateFlow(false)
    val terminalConnected: StateFlow<Boolean> = _terminalConnected.asStateFlow()

    private val _terminalConnecting = MutableStateFlow(false)
    val terminalConnecting: StateFlow<Boolean> = _terminalConnecting.asStateFlow()

    fun openTerminal() {
        _state.update { it.copy(screen = Screen.Terminal) }
        connectTerminal()
    }

    fun connectTerminal() {
        terminal?.close()
        _terminalOutput.value = ""
        _terminalConnecting.value = true
        _terminalConnected.value = false
        val client = TerminalClient(store.baseUrl, store.token)
        client.onOutput = { text -> _terminalOutput.update { (it + text).takeLast(200_000) } }
        client.onCreated = { _, _ ->
            _terminalConnecting.value = false
            _terminalConnected.value = true
        }
        client.onExited = { _, code ->
            _terminalOutput.update { it + "\n[exit $code]\n" }
        }
        client.onError = { message ->
            _terminalConnecting.value = false
            _terminalConnected.value = false
            _terminalOutput.update { it + "\n[error] $message\n" }
        }
        client.onClosed = {
            _terminalConnecting.value = false
            _terminalConnected.value = false
        }
        terminal = client
        client.connect(cols = 100, rows = 40)
    }

    fun terminalInput(data: String) {
        terminal?.sendInput(data)
    }

    fun closeTerminal() {
        terminal?.close()
        terminal = null
        _terminalConnecting.value = false
        _terminalConnected.value = false
    }

    fun clearTerminalOutput() {
        _terminalOutput.value = ""
    }

    // ---- Models --------------------------------------------------------

    fun openModelSettings() {
        _state.update { it.copy(screen = Screen.ModelSettings) }
        refreshModels()
    }

    fun refreshModels() {
        _state.update { it.copy(modelsLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.modelCatalog() } }
                .onSuccess { catalog ->
                    val provider = catalog.groups
                        .firstOrNull { g -> g.models.any { it.id == catalog.default } }
                        ?.provider
                    val limit = if (provider != null && catalog.default.isNotBlank()) {
                        runCatching { withContext(Dispatchers.IO) { api.modelContextLimit(provider, catalog.default) } }.getOrNull()
                    } else {
                        null
                    }
                    _state.update {
                        it.copy(
                            modelCatalog = catalog,
                            modelsLoading = false,
                            selectedModel = catalog.default,
                            contextLimit = limit?.toString().orEmpty(),
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(modelsLoading = false, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun chooseDefaultModel(model: ModelEntry, provider: String) {
        _state.update { it.copy(modelsLoading = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.setDefaultModel(model.id, provider) } }
                .onSuccess {
                    _state.update {
                        it.copy(
                            modelsLoading = false,
                            selectedModel = model.id,
                            notice = str(R.string.model_saved),
                        )
                    }
                    refreshModels()
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(modelsLoading = false, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun setContextLimitInput(value: String) {
        _state.update { it.copy(contextLimit = value.filter(Char::isDigit)) }
    }

    fun saveContextLimit() {
        val snapshot = _state.value
        val model = snapshot.selectedModel
        val limit = snapshot.contextLimit.toLongOrNull()
        val provider = snapshot.modelCatalog?.groups
            ?.firstOrNull { g -> g.models.any { it.id == model } }
            ?.provider
        if (model.isBlank() || limit == null || provider == null) return
        _state.update { it.copy(modelsLoading = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.setModelContextLimit(provider, model, limit) }
            }.onSuccess {
                _state.update {
                    it.copy(modelsLoading = false, notice = str(R.string.model_saved))
                }
            }.onFailure { failure ->
                _state.update {
                    it.copy(modelsLoading = false, error = failure.readableMessage(localized))
                }
            }
        }
    }

    // ---- Studio update -------------------------------------------------

    fun openStudioUpdate() {
        _state.update { it.copy(screen = Screen.StudioUpdate) }
        refreshStudioUpdate()
        detectHermesDeployment()
    }

    fun refreshStudioUpdate() {
        _state.update { it.copy(studioLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    UpdateBundle(
                        preview = api.studioUpdatePreview(),
                        tags = api.studioUpdateTags(),
                        versions = api.serverVersions(),
                        remote = api.remoteVersions(),
                        agents = api.codingAgentPolicies(),
                    )
                }
            }.onSuccess { bundle ->
                _state.update {
                    it.copy(
                        studioPreview = bundle.preview,
                        studioTags = bundle.tags,
                        serverVersions = bundle.versions,
                        remoteVersions = bundle.remote,
                        agentPolicies = bundle.agents,
                        studioLoading = false,
                    )
                }
            }.onFailure { failure ->
                _state.update {
                    it.copy(studioLoading = false, error = failure.readableMessage(localized))
                }
            }
        }
    }

    private data class UpdateBundle(
        val preview: StudioUpdatePreview,
        val tags: List<StudioUpdateTag>,
        val versions: ServerVersions,
        val remote: RemoteVersions?,
        val agents: List<AgentPolicy>,
    )

    /** Update Studio itself. The server may restart, so callers must confirm first. */
    fun updateStudio() {
        val before = _state.value.serverVersions?.studio.orEmpty()
        _state.update {
            it.copy(
                studioLoading = true,
                updateProgress = -1,
                restartRequired = false,
                error = null,
                notice = null,
            )
        }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.triggerStudioUpdate() } }
                .onSuccess {
                    _state.update {
                        it.copy(studioLoading = false, notice = str(R.string.studio_update_started))
                    }
                    awaitStudioUpdate(before)
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(studioLoading = false, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    /**
     * Studio's update endpoint is fire-and-forget, so the only honest progress
     * signal is the installed version changing. Poll it until it does, then ask
     * for the restart that requirement demands.
     */
    private suspend fun awaitStudioUpdate(previousVersion: String) {
        val deadline = System.currentTimeMillis() + 10 * 60 * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(4_000)
            val versions = runCatching { withContext(Dispatchers.IO) { api.serverVersions() } }.getOrNull()
            if (versions != null) {
                if (versions.studio.isNotBlank() && versions.studio != previousVersion) {
                    _state.update {
                        it.copy(
                            serverVersions = versions,
                            updateProgress = 100,
                            restartRequired = true,
                            notice = str(R.string.update_complete_restart),
                        )
                    }
                    return
                }
            }
        }
        // The version never changed; say so instead of spinning forever.
        _state.update {
            it.copy(
                updateProgress = -1,
                notice = str(R.string.update_still_pending),
            )
        }
    }

    /** Install or update one coding agent on the server. */
    fun updateCodingAgent(agentId: String) {
        _state.update { it.copy(studioLoading = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.installCodingAgent(agentId) } }
                .onSuccess {
                    _state.update {
                        it.copy(studioLoading = false, notice = str(R.string.studio_update_started))
                    }
                    refreshStudioUpdate()
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(studioLoading = false, error = failure.readableMessage(localized))
                    }
                }
        }
    }


    // ---- Telegram-style per-chat state --------------------------------

    fun draft(conversationId: String): String = store.draftFor(conversationId)

    fun saveDraft(conversationId: String, text: String) = store.setDraftFor(conversationId, text)

    fun readIndex(conversationId: String, total: Int): Int = store.readIndexFor(conversationId, total)

    fun markRead(conversationId: String, index: Int) = store.setReadIndexFor(conversationId, index)

    // ---- Workspace browser ---------------------------------------------

    private var filesRootCache: String? = null

    /**
     * Opens the folder this conversation actually works in.
     *
     * `session.workspace` is an absolute path, but the file API resolves paths
     * relative to the profile root, so the two are reconciled here once and
     * cached — the root never changes within a connection.
     */
    fun openWorkspace() {
        _state.update { it.copy(screen = Screen.Workspace, workspaceLoading = true, error = null, workspaceFile = null) }
        loadWorkspaceDirectories()
        viewModelScope.launch {
            val root = filesRootCache ?: runCatching {
                withContext(Dispatchers.IO) { api.filesRoot() }
            }.getOrNull().orEmpty().also { if (it.isNotBlank()) filesRootCache = it }
            val absolute = _state.value.openSession?.workspace.orEmpty().ifBlank {
                if (root.isNotBlank()) "$root/workspace" else "workspace"
            }
            val relative = relativeToRoot(absolute, root)
            val entries = runCatching { withContext(Dispatchers.IO) { api.filesList(relative) } }
                .getOrElse { failure ->
                    _state.update {
                        it.copy(workspaceLoading = false, error = failure.readableMessage(localized))
                    }
                    return@launch
                }
            _state.update {
                it.copy(workspacePath = relative, workspaceEntries = entries, workspaceLoading = false)
            }
        }
    }

    /** Reloads whatever directory the browser is currently showing. */
    fun refreshWorkspace() {
        val path = _state.value.workspacePath
        _state.update { it.copy(workspaceLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.filesList(path) } }
                .onSuccess { entries ->
                    _state.update { it.copy(workspaceEntries = entries, workspaceLoading = false) }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(workspaceLoading = false, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun openWorkspaceDir(entry: WorkspaceEntry) {
        _state.update { it.copy(workspaceLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.filesList(entry.path) } }
                .onSuccess { entries ->
                    _state.update {
                        it.copy(workspacePath = entry.path, workspaceEntries = entries, workspaceLoading = false)
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(workspaceLoading = false, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    /** Goes one level up; at the profile root the request is ignored. */
    fun workspaceUp() {
        val path = _state.value.workspacePath
        if (path.isBlank()) return
        val parent = path.substringBeforeLast('/', "")
        _state.update { it.copy(workspaceLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.filesList(parent) } }
                .onSuccess { entries ->
                    _state.update {
                        it.copy(workspacePath = parent, workspaceEntries = entries, workspaceLoading = false)
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(workspaceLoading = false, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun openWorkspaceFile(entry: WorkspaceEntry) {
        _state.update { it.copy(workspaceLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.fileRead(entry.path) } }
                .onSuccess { content ->
                    _state.update {
                        it.copy(
                            workspaceLoading = false,
                            workspaceFile = content,
                            workspaceFileName = entry.name,
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(workspaceLoading = false, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun closeWorkspaceFile() {
        _state.update { it.copy(workspaceFile = null, workspaceFileName = "") }
    }

    /** Download URL for a file, handed to the system browser/downloader. */
    fun workspaceDownloadUrl(entry: WorkspaceEntry): String = api.fileDownloadUrl(entry.path)

    private fun relativeToRoot(absolute: String, root: String): String {
        if (root.isBlank()) return absolute.trim('/')
        if (absolute == root) return ""
        if (absolute.startsWith("$root/")) return absolute.removePrefix("$root/")
        // Unknown root (older server): fall back to the segment after .hermes.
        val marker = "/.hermes/"
        return if (absolute.contains(marker)) absolute.substringAfter(marker) else absolute.trim('/')
    }

    // ---- Hermes Agent update -------------------------------------------

    /** Text of the in-flight run, kept outside the coroutine so re-entering the
     *  conversation can render what has already streamed. */
    private val runAnswer = StringBuilder()
    private val runThinking = StringBuilder()
    private var runStreamed = false
    private var runStartedAtMillis = 0L

    private var opsTerminal: TerminalClient? = null
    private var opsTimeoutJob: Job? = null

    /**
     * Asks the server how Hermes Agent is deployed. `hermes update --plan` is
     * read-only and prints `Install: git (v0.21.7 @ abc1234)` as its first line,
     * which is the authoritative deployment kind — guessing from npm presence
     * would pick the wrong updater on a source checkout.
     */
    fun detectHermesDeployment() {
        if (_state.value.hermesDeploy.isNotBlank()) return
        runServerCommand(
            command = "hermes update --plan 2>&1 | head -12",
            onChunk = { chunk ->
                val kind = Regex("Install:\\s*([a-z]+)", RegexOption.IGNORE_CASE)
                    .find(chunk)?.groupValues?.get(1).orEmpty()
                val version = Regex("v?(\\d+\\.\\d+\\.\\d+)").find(chunk)?.groupValues?.get(1).orEmpty()
                if (kind.isNotBlank() || version.isNotBlank()) {
                    _state.update {
                        it.copy(
                            hermesDeploy = kind.ifBlank { it.hermesDeploy },
                            hermesVersion = version.ifBlank { it.hermesVersion },
                        )
                    }
                }
            },
            onDone = { _ -> },
        )
    }

    /**
     * Updates Hermes Agent through its own updater, which is the only component
     * that knows how this host's install is laid out (git / docker / nix).
     * The server is restarted afterwards, as required for any update.
     */
    fun updateHermes() {
        if (_state.value.hermesUpdating) return
        _state.update {
            it.copy(
                hermesUpdating = true,
                hermesUpdateLog = "",
                serverRestarting = false,
                error = null,
                notice = null,
            )
        }
        runServerCommand(
            command = "hermes update --yes 2>&1",
            onChunk = { chunk ->
                _state.update { it.copy(hermesUpdateLog = (it.hermesUpdateLog + chunk).takeLast(80_000)) }
            },
            onDone = { exit ->
                if (exit == 0) {
                    _state.update {
                        it.copy(
                            hermesUpdating = false,
                            updateProgress = 100,
                            restartRequired = true,
                            hermesUpdateLog = it.hermesUpdateLog + "\n[APP] Hermes updated. Restart the server to activate it.\n",
                        )
                    }
                    // Requirement: every update ends with a restart. It is announced
                    // first so the countdown can be cancelled by tapping now.
                    viewModelScope.launch {
                        delay(5_000)
                        if (_state.value.restartRequired) restartServer()
                    }
                } else {
                    _state.update {
                        it.copy(
                            hermesUpdating = false,
                            updateProgress = -1,
                            error = str(R.string.hermes_update_failed),
                            hermesUpdateLog = it.hermesUpdateLog + "\n[APP] updater exited with ${exit ?: -1}\n",
                        )
                    }
                }
            },
        )
    }

    /** Restarts the Studio server this app talks to, then re-reads its versions. */
    fun restartServer() {
        if (_state.value.serverRestarting) return
        _state.update { it.copy(serverRestarting = true, restartRequired = false, notice = null, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.restartWebUi() } }
                .onFailure { failure ->
                    // The socket dying mid-restart is the expected outcome; only a
                    // clean HTTP error is worth surfacing.
                    if (failure is HermesException && failure.statusCode == 0) {
                        _state.update { it.copy(serverRestarting = false) }
                    }
                }
            // Give the process time to come back before re-reading versions.
            delay(6_000)
            _state.update { it.copy(serverRestarting = false) }
            runCatching { withContext(Dispatchers.IO) { api.serverVersions() } }
                .onSuccess { versions ->
                    _state.update { it.copy(serverVersions = versions, hermesVersion = versions.agent) }
                }
            refreshStudioUpdate()
        }
    }

    /**
     * Runs one shell command on the server through the terminal socket and waits
     * for `printf` to report its exit status. Used only for the updater, which
     * has no REST endpoint of its own.
     */
    private fun runServerCommand(command: String, onChunk: (String) -> Unit, onDone: (Int?) -> Unit) {
        opsTerminal?.close()
        opsTerminal = null
        opsTimeoutJob?.cancel()
        val sentinel = "__EKKO_CMD_DONE__"
        val buffer = StringBuilder()
        var finished = false
        fun finish(exit: Int?) {
            if (finished) return
            finished = true
            opsTimeoutJob?.cancel()
            opsTerminal = null
            onDone(exit)
        }
        val client = TerminalClient(store.baseUrl, store.token)
        client.onCreated = { _, _ ->
            client.sendInput("$command; printf '\\n$sentinel%s\\n' \"$?\"\n")
        }
        client.onOutput = { text ->
            val clean = stripAnsi(text)
            buffer.append(clean)
            val index = clean.indexOf(sentinel)
            if (index >= 0) {
                onChunk(clean.substring(0, index))
                val exit = clean.substring(index + sentinel.length)
                    .trim().takeWhile(Char::isDigit).toIntOrNull()
                finish(exit)
            } else {
                onChunk(clean)
            }
        }
        client.onError = { message ->
            onChunk("\n[connection error] $message\n")
            finish(null)
        }
        client.onClosed = { finish(null) }
        opsTerminal = client
        opsTimeoutJob = viewModelScope.launch {
            delay(20 * 60 * 1000L)
            if (!finished) {
                onChunk("\n[timeout] no completion marker after 20 minutes\n")
                client.close()
                finish(null)
            }
        }
        client.connect(cols = 120, rows = 40)
    }

    private fun stripAnsi(text: String): String = text
        .replace(Regex("\u001B\\][^\u0007]*\u0007"), "")
        .replace(Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]"), "")
        .replace("\r", "")

    /**
     * Runs one preview-upgrade step. The caller must have confirmed first: these
     * actions can restart the very server this app is connected to.
     */
    fun runStudioUpdateAction(action: String, tag: String?) {
        _state.update { it.copy(studioLoading = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.studioUpdateAction(action, tag) }
            }.onSuccess {
                _state.update {
                    it.copy(studioLoading = false, notice = str(R.string.studio_update_started))
                }
                refreshStudioUpdate()
            }.onFailure { failure ->
                _state.update {
                    it.copy(studioLoading = false, error = failure.readableMessage(localized))
                }
            }
        }
    }

    // ---- MCP live tools --------------------------------------------------

    /**
     * Fetches what the server actually exposes right now and opens it for one
     * server. The list in the editor is the saved config; this is reality.
     */
    fun openMcpTools(server: String) {
        _state.update { it.copy(mcpToolsFor = server, mcpToolsLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.mcpTools() } }
                .onSuccess { live ->
                    _state.update { it.copy(mcpLiveTools = live, mcpToolsLoading = false) }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(mcpToolsLoading = false, mcpToolsFor = null, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun closeMcpTools() = _state.update { it.copy(mcpToolsFor = null, mcpLiveTools = emptyList()) }

    // ---- Workspace directories ------------------------------------------

    fun loadWorkspaceDirectories() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.workspaceDirectories() } }
                .onSuccess { list -> _state.update { it.copy(workspaceDirectories = list) } }
                .onFailure { /* shortcuts are optional; the browser still works */ }
        }
    }

    /** Saves the folder currently open in the browser, or removes it again. */
    fun toggleWorkspaceFavorite(path: String) {
        if (path.isBlank()) return
        val current = _state.value.workspaceDirectories.firstOrNull { it.path == path }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (current == null) {
                        api.addWorkspaceDirectory(path)
                        api.setWorkspaceDirectoryFavorite(path, true)
                    } else {
                        api.setWorkspaceDirectoryFavorite(path, !current.favorite)
                    }
                }
            }
                .onSuccess {
                    _state.update {
                        it.copy(notice = str(if (current == null) R.string.workspace_saved else R.string.workspace_shortcut_updated))
                    }
                    loadWorkspaceDirectories()
                }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.readableMessage(localized)) }
                }
        }
    }

    // ---- Skill bundles --------------------------------------------------

    fun loadBundles() {
        if (_state.value.bundlesLoading) return
        _state.update { it.copy(bundlesLoading = true) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.bundles() } }
                .onSuccess { list -> _state.update { it.copy(bundles = list, bundlesLoading = false) } }
                .onFailure { failure ->
                    _state.update {
                        it.copy(bundlesLoading = false, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun createBundle(name: String, description: String, skills: List<String>) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.createBundle(name.trim(), description.trim(), skills) }
            }
                .onSuccess {
                    _state.update { it.copy(notice = str(R.string.bundle_created, name.trim())) }
                    loadBundles()
                }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.readableMessage(localized)) }
                }
        }
    }

    fun deleteBundle(bundle: SkillBundle) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.deleteBundle(bundle.command) } }
                .onSuccess {
                    _state.update { it.copy(notice = str(R.string.bundle_deleted, bundle.name)) }
                    loadBundles()
                }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.readableMessage(localized)) }
                }
        }
    }

    // ---- Write gate ----------------------------------------------------

    fun openWriteGate() {
        _state.update { it.copy(screen = Screen.WriteGate) }
        refreshWriteGate()
    }

    /**
     * Quiet poll for the write gate while the app is open.
     *
     * Hermes blocks a tool call until a human approves it, and nothing on the
     * socket announces that for every tool — so without this the phone showed no
     * sign of a run sitting blocked. Failures are swallowed: a 404 or a dropped
     * connection must not paint an error over whatever the user is doing.
     */
    fun pollWriteGate() {
        if (_state.value.writeGateLoading) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.writeGatePending(currentProfile()) } }
                .onSuccess { records ->
                    _state.update { it.copy(writeGateRecords = records) }
                }
        }
    }

    fun refreshWriteGate() {
        val profile = _state.value.activeProfile.ifBlank { "default" }
        _state.update { it.copy(writeGateLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.writeGatePending(profile) } }
                .onSuccess { records ->
                    _state.update { it.copy(writeGateRecords = records, writeGateLoading = false) }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(writeGateLoading = false, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun decideWriteGate(record: WriteGateRecord, approve: Boolean) {
        if (_state.value.writeGateBusyId != null) return
        val profile = _state.value.activeProfile.ifBlank { "default" }
        _state.update { it.copy(writeGateBusyId = record.id, error = null, notice = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.decideWriteGate(profile, record, approve) }
            }.onSuccess {
                _state.update {
                    it.copy(
                        writeGateBusyId = null,
                        writeGateRecords = it.writeGateRecords.filterNot { r -> r.id == record.id },
                        notice = str(
                            if (approve) R.string.write_gate_approved else R.string.write_gate_rejected,
                        ),
                    )
                }
            }.onFailure { failure ->
                _state.update {
                    it.copy(writeGateBusyId = null, error = failure.readableMessage(localized))
                }
            }
        }
    }

    fun login(baseUrl: String, username: String, password: String, remember: Boolean = false, label: String = "") {
        val normalized = normalizeUrl(baseUrl)
        if (normalized == null) {
            _state.update { it.copy(error = str(R.string.error_server_address), devicesUi = it.devicesUi.copy(saving = false)) }
            return
        }
        if (username.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = str(R.string.error_credentials_required), devicesUi = it.devicesUi.copy(saving = false)) }
            return
        }

        launchWork(
            work = {
                api.update(normalized, "")
                val token = api.login(username.trim(), password)
                store.baseUrl = normalized
                store.token = token
                // .remember-check only persists after a successful sign-in, so a
                // typo never saves a broken password.
                store.rememberCredentials = remember
                if (remember) {
                    store.savedUsername = username.trim()
                    store.savedPassword = password
                } else {
                    store.savedUsername = ""
                    store.savedPassword = ""
                }
                // [label] is the official pairing panel's 设备名称; the login form
                // leaves it blank and the host stands in.
                val deviceName = label.trim().take(40).ifBlank { hostLabel(normalized) }
                store.instances = upsertInstance(
                    store.instances,
                    StudioInstance(
                        url = normalized,
                        label = deviceName,
                        username = username.trim(),
                        token = token,
                        lastUsedAt = System.currentTimeMillis(),
                        endpointKind = DEFAULT_ENDPOINT_KIND,
                    ),
                )
                api.update(normalized, token)
                chat.update(normalized, token)
                group.update(normalized, token)
                val user = api.currentUser()
                val versions = runCatching { api.runtimeVersions() }.getOrNull()
                SessionBootstrap(user, api.profiles(), api.sessions(null)) to versions
            },
            onSuccess = { (bootstrap, versions) ->
                val (user, profiles, sessions) = bootstrap
                if (versions != null) {
                    rememberDeviceDetails(
                        normalized,
                        DeviceProbe(
                            online = true,
                            username = user.username,
                            webUiVersion = versions.activeWebUi,
                            platform = versions.platform,
                            checkedAt = System.currentTimeMillis() / 1000,
                        ),
                        user.username,
                    )
                } else {
                    rememberDeviceDetails(
                        normalized,
                        DeviceProbe(online = true, username = user.username, checkedAt = System.currentTimeMillis() / 1000),
                        user.username,
                    )
                }
                _state.update {
                    it.copy(
                        screen = Screen.Chats,
                        baseUrl = normalized,
                        account = user.username,
                        currentUser = user,
                        profiles = profiles,
                        activeProfile = pickProfile(profiles),
                        sessions = sessions,
                        instances = store.instances,
                        devicesUi = it.devicesUi.copy(saving = false),
                        error = null,
                    )
                }
                syncBranding()
            },
            onFailure = { failure ->
                // The pairing panel's submit spinner is driven by `saving`, which
                // `launchWork` knows nothing about.
                _state.update {
                    it.copy(
                        error = failure.readableMessage(localized),
                        devicesUi = it.devicesUi.copy(saving = false),
                    )
                }
            },
        )
    }

    /** Pulls the Studio logo from the connected server for the launch screen. */
    private fun syncBranding(force: Boolean = false) {
        viewModelScope.launch {
            runCatching { AppLogo.syncFromServer(getApplication<Application>(), api, force) }
        }
    }

    // ── lists ─────────────────────────────────────────────────────────────

    fun showTab(tab: Tab) {
        if (_state.value.screen == Screen.Conversation) cancelActiveRun(abort = false)
        navigationHistory.clear()
        _state.update { it.copy(tab = tab, error = null) }
        when (tab) {
            Tab.Chats -> {
                _state.update { it.copy(screen = Screen.Chats) }
                if (_state.value.sessions.isEmpty()) refreshSessions()
            }
            Tab.Groups -> {
                _state.update { it.copy(screen = Screen.Groups) }
                if (_state.value.rooms.isEmpty()) refreshRooms()
            }
            Tab.Agent -> {
                _state.update { it.copy(screen = Screen.AgentHub) }
                refreshServerConfig()
                if (_state.value.cronJobs.isEmpty()) refreshCronJobs()
            }
        }
    }

    fun refreshSessions() {
        if (_state.value.refreshingSessions) return
        val profile = _state.value.profileFilter.ifBlank { null }
        _state.update { it.copy(refreshingSessions = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.sessions(profile, _state.value.sessionLimit) } }
                .onSuccess { sessions ->
                    store.sessionsSnapshot = api.sessionsToJson(sessions)
                    _state.update { it.copy(sessions = sessions, refreshingSessions = false) }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            refreshingSessions = false,
                            error = failure.readableMessage(localized),
                        )
                    }
                }
        }
    }
    fun loadMoreSessions() { _state.update { it.copy(sessionLimit = it.sessionLimit + 80) }; refreshSessions() }

    fun searchSessions(query: String) {
        if (query.isBlank()) { _state.update { it.copy(sessionSearchResults = null) }; return }
        val profile = _state.value.profileFilter.ifBlank { null }
        viewModelScope.launch {
            delay(250)
            runCatching { withContext(Dispatchers.IO) { api.searchSessions(query.trim(), profile) } }
                .onSuccess { results -> _state.update { it.copy(sessionSearchResults = results) } }
        }
    }

    fun loadSessionCategories() {
        viewModelScope.launch { runCatching { withContext(Dispatchers.IO) { api.sessionCategories() } }.onSuccess { categories -> _state.update { it.copy(sessionCategories = categories) } } }
    }

    fun createSessionCategory(name: String) = launchWork(
        work = { api.createSessionCategory(name) },
        onSuccess = { category -> _state.update { it.copy(sessionCategories = it.sessionCategories + category) } },
    )
    fun renameSessionCategory(category: SessionCategory, name: String) = launchWork(work = { api.renameSessionCategory(category.id, name) }, onSuccess = { loadSessionCategories() })
    fun deleteSessionCategory(category: SessionCategory) = launchWork(work = { api.deleteSessionCategory(category.id) }, onSuccess = { loadSessionCategories(); refreshSessions() })

    fun setSessionCategory(session: SessionSummary, categoryId: Int?) = launchWork(
        work = { api.setSessionCategory(session.id, categoryId) },
        onSuccess = { refreshSessions(); _state.update { it.copy(sessionSearchResults = null) } },
    )

    fun archiveSession(session: SessionSummary) = launchWork(
        work = { api.archiveSession(session.id, !session.archived) },
        onSuccess = { refreshSessions(); _state.update { it.copy(sessionSearchResults = null, notice = str(if (session.archived) R.string.session_unarchived else R.string.session_archived)) } },
    )
    fun pinSession(session: SessionSummary) = launchWork(
        work = { api.setSessionPinned(session.id, !session.pinned) },
        onSuccess = { refreshSessions(); _state.update { it.copy(sessionSearchResults = null, notice = str(if (session.pinned) R.string.session_unpinned else R.string.session_pinned)) } },
    )
    fun setSessionWorkspace(session: SessionSummary, workspace: String) = launchWork(work = { api.setSessionWorkspace(session.id, workspace) }, onSuccess = { refreshSessions() })
    fun exportSession(session: SessionSummary) { val safe = session.title.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { session.id }; val request = DownloadManager.Request(Uri.parse(api.sessionExportUrl(session.id))).setTitle("$safe.json").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$safe.json"); store.token.takeIf(String::isNotBlank)?.let { request.addRequestHeader("Authorization", "Bearer $it") }; getApplication<Application>().getSystemService(DownloadManager::class.java)?.enqueue(request) }
    fun batchDeleteVisibleSessions() { val ids = (_state.value.sessionSearchResults ?: _state.value.sessions).map { it.id }; if (ids.isEmpty()) return; launchWork(work = { api.batchDeleteSessions(ids) }, onSuccess = { refreshSessions() }) }

    fun openWorkflows() {
        _state.update { it.copy(screen = Screen.Workflows, loadingWorkflows = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.workflows(_state.value.profileFilter.ifBlank { null }) } }
                .onSuccess { workflows ->
                    val runs = withContext(Dispatchers.IO) { workflows.associate { it.id to runCatching { api.workflowRuns(it.id) }.getOrDefault(emptyList()) } }
                    val schedules = withContext(Dispatchers.IO) { workflows.associate { it.id to runCatching { api.workflowSchedules(it.id) }.getOrDefault(emptyList()) } }
                    _state.update { it.copy(workflows = workflows, workflowRuns = runs, workflowSchedules = schedules, loadingWorkflows = false) }
                }.onFailure { failure -> _state.update { it.copy(loadingWorkflows = false, error = failure.readableMessage(localized)) } }
        }
    }

    fun runWorkflow(workflow: StudioWorkflow, input: String?) = launchWork(
        work = { api.runWorkflow(workflow.id, input) },
        onSuccess = { openWorkflows() },
    )

    /** Opens one run's detail; the node sessions are what make it useful. */
    fun openWorkflowRun(run: StudioWorkflowRun) {
        _state.update { it.copy(screen = Screen.WorkflowRun, openWorkflowRun = run, openWorkflowRunId = run.id, error = null) }
        reloadWorkflowRun()
    }

    fun reloadWorkflowRun() {
        val runId = _state.value.openWorkflowRunId ?: return
        val workflowId = _state.value.openWorkflowRun?.workflowId
            ?: _state.value.workflows.firstOrNull { flow -> _state.value.workflowRuns[flow.id]?.any { it.id == runId } == true }?.id
            ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.workflowRun(workflowId, runId) } }
                .onSuccess { fresh -> _state.update { it.copy(openWorkflowRun = fresh) } }
                .onFailure { failure -> _state.update { it.copy(error = failure.readableMessage(localized)) } }
        }
    }

    /** Node-level approval: the run detail knows which node is blocked. */
    fun approveWorkflowNode(workflowId: String, runId: String, nodeId: String, approved: Boolean) = launchWork(
        work = { api.approveWorkflowNode(workflowId, runId, nodeId, approved) },
        onSuccess = { reloadWorkflowRun() },
    )

    fun stopWorkflowRun(workflowId: String, runId: String) = launchWork(
        work = { api.stopWorkflowRun(workflowId, runId) },
        onSuccess = {
            reloadWorkflowRun()
            openWorkflows()
        },
    )

    fun stopWorkflowRun(run: StudioWorkflowRun) = launchWork(
        work = { api.stopWorkflowRun(run.workflowId, run.id) },
        onSuccess = { openWorkflows() },
    )

    fun approveWorkflowNode(run: StudioWorkflowRun, approved: Boolean) {
        val node = run.pendingNodeId ?: return
        launchWork(work = { api.approveWorkflowNode(run.workflowId, run.id, node, approved) }, onSuccess = { openWorkflows() })
    }
    fun saveWorkflow(item: StudioWorkflow?, name: String, workspace: String, nodes: String, edges: String) = launchWork(work = { if (item == null) api.createWorkflow(name, _state.value.profileFilter.ifBlank { null }, workspace, nodes, edges) else api.updateWorkflow(item.id, name, workspace, nodes, edges) }, onSuccess = { openWorkflows() })
    fun deleteWorkflow(item: StudioWorkflow) = launchWork(work = { api.deleteWorkflow(item.id) }, onSuccess = { openWorkflows() })
    fun deleteAllWorkflows() = launchWork(work = { api.batchDeleteWorkflows(_state.value.workflows.map { it.id }) }, onSuccess = { openWorkflows() })
    fun importWorkflow(document: String) = launchWork(work = { api.importWorkflow(document, _state.value.profileFilter.ifBlank { null }) }, onSuccess = { openWorkflows() })
    fun exportWorkflow(item: StudioWorkflow) { val request = DownloadManager.Request(Uri.parse(api.workflowExportUrl(item.id))).setTitle("${item.name}.hermes-workflow.json").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${item.name.replace(Regex("[^A-Za-z0-9._-]"), "_")}.hermes-workflow.json"); store.token.takeIf(String::isNotBlank)?.let { request.addRequestHeader("Authorization", "Bearer $it") }; getApplication<Application>().getSystemService(DownloadManager::class.java)?.enqueue(request) }
    fun deleteWorkflowRun(run: StudioWorkflowRun) = launchWork(work = { api.deleteWorkflowRun(run.workflowId, run.id) }, onSuccess = { openWorkflows() })
    fun rerunWorkflow(run: StudioWorkflowRun) { val node = run.pendingNodeId ?: return; launchWork(work = { api.rerunWorkflow(run.workflowId, run.id, node) }, onSuccess = { openWorkflows() }) }
    fun createWorkflowSchedule(item: StudioWorkflow, expression: String, timezone: String) = launchWork(work = { api.createWorkflowSchedule(item.id, expression, timezone) }, onSuccess = { openWorkflows() })
    fun toggleWorkflowSchedule(item: WorkflowSchedule) = launchWork(work = { api.toggleWorkflowSchedule(item) }, onSuccess = { openWorkflows() })
    fun deleteWorkflowSchedule(item: WorkflowSchedule) = launchWork(work = { api.deleteWorkflowSchedule(item) }, onSuccess = { openWorkflows() })

    fun openGlobalAgent() = _state.update { it.copy(screen = Screen.GlobalAgent, error = null) }

    fun openEkkoHub() {
        _state.update { it.copy(screen = Screen.EkkoHub, loadingEkko = true, error = null) }
        val profile = currentProfile()
        viewModelScope.launch {
            val memory = runCatching { withContext(Dispatchers.IO) { api.ekkoMemories(profile) } }
            val skills = runCatching { withContext(Dispatchers.IO) { api.ekkoSkills(profile) } }
            val mcp = runCatching { withContext(Dispatchers.IO) { api.ekkoMcpServers(profile) } }
            val directories = runCatching { withContext(Dispatchers.IO) { api.ekkoExternalDirectories(profile) } }
            val error = memory.exceptionOrNull() ?: skills.exceptionOrNull() ?: mcp.exceptionOrNull() ?: directories.exceptionOrNull()
            _state.update { it.copy(ekkoMemories = memory.getOrDefault(emptyList()), ekkoSkills = skills.getOrDefault(emptyList()), ekkoMcpServers = mcp.getOrDefault(emptyList()), ekkoExternalDirectories = directories.getOrDefault(emptyList()), loadingEkko = false, error = error?.readableMessage(localized)) }
        }
    }

    fun saveEkkoMemory(memory: EkkoMemory, title: String, content: String) = launchWork(work = { api.updateEkkoMemory(currentProfile(), memory, title, content) }, onSuccess = { openEkkoHub() })
    fun deleteEkkoMemory(memory: EkkoMemory) = launchWork(work = { api.deleteEkkoMemory(currentProfile(), memory) }, onSuccess = { openEkkoHub() })
    fun toggleEkkoSkill(skill: SkillInfo) = launchWork(work = { api.setEkkoSkillEnabled(currentProfile(), skill.name, !skill.enabled) }, onSuccess = { openEkkoHub() })
    fun saveEkkoMcp(original: String?, name: String, config: String) = launchWork(work = { api.saveEkkoMcpServer(currentProfile(), original, name, config) }, onSuccess = { openEkkoHub() })
    fun toggleEkkoMcp(server: EkkoMcpServer) = launchWork(work = { api.toggleEkkoMcpServer(currentProfile(), server) }, onSuccess = { openEkkoHub() })
    fun testEkkoMcp(server: EkkoMcpServer) = launchWork(work = { api.testEkkoMcpServer(currentProfile(), server.name) }, onSuccess = { _state.update { it.copy(notice = str(R.string.ekko_mcp_test_ok)) } })
    fun deleteEkkoMcp(server: EkkoMcpServer) = launchWork(work = { api.deleteEkkoMcpServer(currentProfile(), server.name) }, onSuccess = { openEkkoHub() })

    fun restartProfile(profile: String) = launchWork(work = { api.restartProfileRuntime(profile) }, onSuccess = { refreshProfiles(); _state.update { it.copy(notice = str(R.string.profile_restarted)) } })
    fun refreshProviderModels(provider: String) = launchWork(work = { api.refreshProviderModels(currentProfile(), provider) }, onSuccess = { loadModelProviders(); _state.update { it.copy(notice = str(R.string.models_refreshed)) } })
    fun testProvider(provider: String) = launchWork(work = { api.testProvider(currentProfile(), provider) }, onSuccess = { result -> _state.update { it.copy(notice = result) } })

    fun openFiles(path: String = "") = launchWork(work = { api.studioFiles(currentProfile(), path) }, onSuccess = { files -> _state.update { it.copy(screen = Screen.Files, filesPath = path, studioFiles = files, openFile = null, error = null) } })
    fun openStudioFile(file: StudioFile) = launchWork(work = { api.readStudioFile(currentProfile(), file.path) }, onSuccess = { content -> _state.update { it.copy(openFile = file, openFileContent = content) } })
    fun closeStudioFile() = _state.update { it.copy(openFile = null, openFileContent = "") }
    fun saveStudioFile(content: String) { val file = _state.value.openFile ?: return; launchWork(work = { api.writeStudioFile(currentProfile(), file.path, content) }, onSuccess = { _state.update { it.copy(openFileContent = content, notice = str(R.string.files_saved)) } }) }
    fun createStudioFolder(name: String) { val path = listOf(_state.value.filesPath, name).filter(String::isNotBlank).joinToString("/"); launchWork(work = { api.mkdirStudioFile(currentProfile(), path) }, onSuccess = { openFiles(_state.value.filesPath) }) }
    fun renameStudioFile(file: StudioFile, name: String) { val target = file.path.substringBeforeLast('/', "").let { if (it.isBlank()) name else "$it/$name" }; launchWork(work = { api.renameStudioFile(currentProfile(), file.path, target) }, onSuccess = { openFiles(_state.value.filesPath) }) }
    fun copyStudioFile(file: StudioFile, destination: String) = launchWork(work = { api.copyStudioFile(currentProfile(), file.path, destination) }, onSuccess = { openFiles(_state.value.filesPath) })
    fun deleteStudioFile(file: StudioFile) = launchWork(work = { api.deleteStudioFile(currentProfile(), file) }, onSuccess = { openFiles(_state.value.filesPath) })
    fun studioFileUrl(file: StudioFile): String = api.studioFilePreviewUrl(currentProfile(), file.path)
    fun uploadStudioFile(bytes: ByteArray, name: String, mime: String) = launchWork(work = { api.uploadStudioFile(currentProfile(), _state.value.filesPath, bytes, name, mime) }, onSuccess = { openFiles(_state.value.filesPath) })

    fun openLogs() = launchWork(work = { api.studioLogs() }, onSuccess = { logs -> _state.update { it.copy(screen = Screen.Logs, studioLogs = logs, openLog = null, error = null) } })
    fun openLog(log: StudioLogFile) = launchWork(work = { api.studioLog(log.name, currentProfile()) }, onSuccess = { entries -> _state.update { it.copy(openLog = log, logEntries = entries) } })
    fun closeLog() = _state.update { it.copy(openLog = null, logEntries = emptyList()) }

    /**
     * The connections screen is built on the endpoints that actually exist on
     * 0.7.x: GET /api/app-connections, GET /api/app-relay/status and the
     * authorization-code endpoints. The old /api/devices routes, pairing-link
     * and /devices/peer-connections routes all return 404, so the screen used to
     * open empty with an error.
     */
    fun openConnections() {
        _state.update { it.copy(screen = Screen.Connections, error = null, notice = null) }
        refreshConnections()
    }

    fun refreshConnections() {
        _state.update { it.copy(connectionsLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    Triple(
                        api.appConnections(),
                        runCatching { api.appRelayStatus() }.getOrNull(),
                        runCatching { api.serverVersions() }.getOrNull(),
                    )
                }
            }
                .onSuccess { (connections, relay, versions) ->
                    _state.update {
                        it.copy(
                            connectionsLoading = false,
                            appConnections = connections,
                            appRelay = relay ?: it.appRelay,
                            serverVersions = versions ?: it.serverVersions,
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(connectionsLoading = false, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    /** LAN pairing code: the server returns a full QR payload for the phone app. */
    fun requestPairingCode(cloud: Boolean) = launchWork(
        work = { api.createAppAuthorization(cloud) },
        onSuccess = { auth -> _state.update { it.copy(appAuthorization = auth, notice = str(R.string.pairing_code_ready)) } },
    )
    fun connectRelay() = launchWork(work = { api.connectAppRelay() }, onSuccess = { relay -> _state.update { it.copy(appRelay = relay) } })
    fun refreshRelayCode() = launchWork(work = { api.refreshAppRelayCode() }, onSuccess = { relay -> _state.update { it.copy(appRelay = relay) } })
    fun disconnectRelay() = launchWork(work = { api.disconnectAppRelay() }, onSuccess = { relay -> _state.update { it.copy(appRelay = relay) } })
    fun createAppAuthorization(cloud: Boolean) = launchWork(work = { api.createAppAuthorization(cloud) }, onSuccess = { auth -> _state.update { it.copy(appAuthorization = auth) } })
    fun revokeAppConnection(connection: AppConnection) = launchWork(work = { api.revokeAppConnection(connection.id) }, onSuccess = { openConnections() })

    fun switchActiveProfile(name: String) = launchWork(work = { api.switchActiveProfile(name) }, onSuccess = { selectProfile(name); refreshProfiles() })
    fun importProfile(bytes: ByteArray, name: String) = launchWork(work = { api.importProfile(bytes, name) }, onSuccess = { refreshProfiles() })
    fun profileExportUrl(name: String): String = api.profileExportUrl(name)
    fun updateProfileAvatar(name: String, dataUrl: String) = launchWork(work = { api.updateProfileAvatar(name, dataUrl) }, onSuccess = { refreshProfiles() })
    fun clearProfileAvatar(name: String) = launchWork(work = { api.clearProfileAvatar(name) }, onSuccess = { refreshProfiles() })

    fun saveEkkoSkill(name: String, content: String, creating: Boolean) = launchWork(work = { if (creating) api.createEkkoSkill(currentProfile(), name, content) else api.saveEkkoSkill(currentProfile(), name, content) }, onSuccess = { openEkkoHub() })
    fun deleteEkkoSkill(skill: SkillInfo) = launchWork(work = { api.deleteEkkoSkill(currentProfile(), skill.name) }, onSuccess = { openEkkoHub() })
    fun importEkkoSkill(bytes: ByteArray, name: String) = launchWork(work = { api.importEkkoSkill(currentProfile(), bytes, name) }, onSuccess = { openEkkoHub() })
    fun openEkkoSkill(skill: SkillInfo) = launchWork(work = { Triple(api.ekkoSkillDetail(currentProfile(), skill.name), api.ekkoSkillFiles(currentProfile(), skill.name), skill) }, onSuccess = { (content, files, selected) -> _state.update { it.copy(ekkoOpenSkill = selected, ekkoSkillContent = content, ekkoSkillFiles = files) } })
    fun closeEkkoSkill() = _state.update { it.copy(ekkoOpenSkill = null, ekkoSkillContent = "", ekkoSkillFiles = emptyList(), ekkoSkillFilePreviewPath = null, ekkoSkillFilePreviewContent = "") }
    fun openEkkoSkillFile(path: String) { val skill = _state.value.ekkoOpenSkill ?: return; launchWork(work = { api.ekkoSkillFile(currentProfile(), skill.name, path) }, onSuccess = { content -> _state.update { it.copy(ekkoSkillFilePreviewPath = path, ekkoSkillFilePreviewContent = content) } }) }
    fun saveExternalDirectories(lines: String) = launchWork(work = { api.saveEkkoExternalDirectories(currentProfile(), lines.lines().map(String::trim).filter(String::isNotBlank)) }, onSuccess = { openEkkoHub() })

    fun downloadProfile(name: String) {
        val request = DownloadManager.Request(Uri.parse(api.profileExportUrl(name))).setTitle("hermes-profile-$name.tar.gz").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "hermes-profile-$name.tar.gz")
        store.token.takeIf(String::isNotBlank)?.let { request.addRequestHeader("Authorization", "Bearer $it") }
        getApplication<Application>().getSystemService(DownloadManager::class.java)?.enqueue(request)
        _state.update { it.copy(notice = str(R.string.profile_export_started)) }
    }

    fun openJourney() = launchWork(work = { api.journey() to api.skillUsage() }, onSuccess = { (journey, usage) -> _state.update { it.copy(screen = Screen.Journey, journey = journey, skillUsage = usage) } })
    fun openWebhooks() = launchWork(work = { api.webhooks() to api.webhookEvents() }, onSuccess = { (hooks, events) -> _state.update { it.copy(screen = Screen.Webhooks, webhooks = hooks, webhookEvents = events) } })
    fun createWebhook(name: String, url: String) = launchWork(work = { api.createWebhook(name, url) }, onSuccess = { openWebhooks() })
    fun toggleWebhook(item: WebhookEndpoint) = launchWork(work = { api.toggleWebhook(item) }, onSuccess = { openWebhooks() })
    fun updateWebhook(item: WebhookEndpoint, name: String, url: String) = launchWork(work = { api.updateWebhook(item.id, name, url) }, onSuccess = { openWebhooks() })
    fun deleteWebhook(item: WebhookEndpoint) = launchWork(work = { api.deleteWebhook(item.id) }, onSuccess = { openWebhooks() })
    fun testWebhook(item: WebhookEndpoint) = launchWork(work = { api.testWebhook(item.id) }, onSuccess = { result -> _state.update { it.copy(notice = result) } })
    fun clearWebhookEvents() = launchWork(work = { api.clearWebhookEvents() }, onSuccess = { openWebhooks() })
    fun openRuntimeVersions() = launchWork(
        work = { api.runtimeVersions() to api.runtimeJobs() },
        onSuccess = { (versions, jobs) ->
            _state.update { it.copy(screen = Screen.RuntimeVersions, runtimeVersions = versions, runtimeJobs = jobs) }
        },
    )

    /** Jobs outlive the screen, so the poller asks for them on its own. */
    fun refreshRuntimeJobs() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.runtimeJobs() } }
                .onSuccess { jobs -> _state.update { it.copy(runtimeJobs = jobs) } }
        }
    }

    fun openPricing() = launchWork(
        work = { api.usagePricing() },
        onSuccess = { rates ->
            _state.update { it.copy(screen = Screen.Pricing, pricingRates = rates, pricingLoaded = true, pricingDirty = false) }
        },
    )

    /** `index` null appends; an empty provider/model is rejected before it can be saved. */
    fun upsertPricingRate(index: Int?, rate: PricingRate) {
        _state.update { state ->
            val next = state.pricingRates.toMutableList()
            if (index != null && index in next.indices) next[index] = rate else next.add(rate)
            state.copy(pricingRates = next, pricingDirty = true)
        }
    }

    fun removePricingRate(index: Int) {
        _state.update { state ->
            if (index !in state.pricingRates.indices) return@update state
            state.copy(pricingRates = state.pricingRates.filterIndexed { i, _ -> i != index }, pricingDirty = true)
        }
    }

    fun savePricing() {
        val rates = _state.value.pricingRates.filter { it.provider.isNotBlank() && it.model.isNotBlank() }
        _state.update { it.copy(pricingSaving = true) }
        launchWork(
            work = { api.saveUsagePricing(rates) },
            onSuccess = {
                _state.update { it.copy(pricingRates = rates, pricingSaving = false, pricingDirty = false) }
            },
            onFailure = { failure ->
                _state.update { it.copy(pricingSaving = false, error = failure.readableMessage(localized)) }
            },
        )
    }

    /** A per-session token sheet, for the conversation currently on screen. */
    fun openSessionUsage() {
        val sessionId = _state.value.openSession?.id ?: return
        launchWork(
            work = { api.sessionUsage(sessionId) to api.usagePricing() },
            onSuccess = { (usage, rates) ->
                _state.update { it.copy(sessionUsage = usage, pricingRates = rates, pricingLoaded = true) }
            },
        )
    }

    fun closeSessionUsage() = _state.update { it.copy(sessionUsage = null) }

    fun openApiKeys() = launchWork(
        work = { api.modelApiKeys() to api.modelApiCatalog().filter { model -> model.responsesSupported } },
        onSuccess = { (keys, catalog) ->
            _state.update {
                it.copy(screen = Screen.ApiKeys, modelApiKeys = keys, modelApiKeysLoaded = true, modelApiCatalog = catalog)
            }
        },
    )

    fun createApiKey(name: String, models: List<String>, budgetUsd: Double?) = launchWork(
        work = { api.createModelApiKey(name, models, budgetUsd) },
        onSuccess = { created ->
            _state.update {
                it.copy(modelApiKeys = listOf(created.key) + it.modelApiKeys, createdApiKeySecret = created.secret)
            }
        },
    )

    /** The plaintext only exists in memory until the dialog is dismissed. */
    fun dismissCreatedApiKey() = _state.update { it.copy(createdApiKeySecret = null) }

    fun editApiKey(key: ModelApiKey, name: String, models: List<String>, budgetUsd: Double?) = launchWork(
        work = { api.editModelApiKey(key.id, name, models, budgetUsd) },
        onSuccess = { updated ->
            _state.update { state ->
                state.copy(modelApiKeys = state.modelApiKeys.map { if (it.id == updated.id) updated else it })
            }
        },
    )

    fun revokeApiKey(key: ModelApiKey) = launchWork(
        work = { api.deleteModelApiKey(key.id) },
        onSuccess = {
            _state.update { state -> state.copy(modelApiKeys = state.modelApiKeys.filterNot { it.id == key.id }) }
        },
    )

    fun deleteRuntimeVersion(version: String, webUi: Boolean) = launchWork(
        work = { api.deleteRuntimeVersion(version, webUi) },
        onSuccess = { openRuntimeVersions() },
    )
    fun activateVersion(version: RuntimeVersion) = launchWork(work = { api.activateVersion(version.version, version.kind == "webui") }, onSuccess = { openRuntimeVersions() })
    fun downloadVersion(version: String, webUi: Boolean) = launchWork(work = { api.downloadVersion(version, webUi) }, onSuccess = { openRuntimeVersions() })
    fun restartWebUi() = launchWork(work = { api.restartWebUi() }, onSuccess = { openRuntimeVersions() })
    fun openAppearance() = launchWork(work = { api.themeSettings() }, onSuccess = { theme -> _state.update { it.copy(screen = Screen.Appearance, themeSettings = theme) } })
    fun saveTheme(fontSize: Int, text: String, accent: String) = launchWork(work = { api.updateTheme(fontSize, text, accent) }, onSuccess = { openAppearance() })
    fun removeThemeBackground() = launchWork(work = { api.removeThemeBackground() }, onSuccess = { openAppearance() })
    fun uploadThemeBackground(bytes: ByteArray, name: String, mime: String) = launchWork(work = { api.uploadThemeBackground(bytes, name, mime) }, onSuccess = { openAppearance() })
    fun loadKanbanOperations(taskId: String? = null) { val board = _state.value.kanban.board; if (board.isBlank()) return; launchWork(work = { val diagnostics = api.kanbanDiagnostics(board, taskId); val stats = api.kanbanStats(board); val extras = taskId?.let { api.kanbanLog(board, it) to api.kanbanAttachments(board, it) } ?: ("" to emptyList()); Triple(stats, diagnostics, extras) }, onSuccess = { (stats, diagnostics, extras) -> _state.update { it.copy(kanbanStats = stats, kanbanDiagnostics = diagnostics, kanbanLog = extras.first, kanbanAttachments = extras.second) } }) }
    fun kanbanCommand(task: KanbanTask, action: String, value: String = "") { val board = _state.value.kanban.board; launchWork(work = { api.kanbanCommand(board, task.id, action, value) }, onSuccess = { loadKanbanTask(task.id); loadKanbanOperations(task.id) }) }

    fun startGlobalAgentConversation() {
        startNewConversation(AgentRuntimeSelection("ekko-agent", "ekko", "Global Agent", globalAgent = true))
    }

    fun refreshRooms() = launchWork(
        work = { api.rooms() },
        onSuccess = { rooms -> _state.update { it.copy(rooms = rooms) } },
    )

    fun setProfileFilter(profile: String) {
        _state.update { it.copy(profileFilter = profile) }
        refreshSessions()
    }

    fun refreshProfiles() = launchWork(
        work = { api.profiles() to runCatching { api.profileRuntimeStatuses() }.getOrDefault(emptyMap()) },
        onSuccess = { (profiles, statuses) ->
            _state.update { it.copy(profiles = profiles, activeProfile = pickProfile(profiles), profileRuntimeStatuses = statuses) }
        },
    )

    fun selectProfile(name: String) {
        cancelActiveRun(abort = false)
        historyJob?.cancel()
        historyJob = null
        store.profile = name
        _state.update {
            it.copy(
                activeProfile = name,
                screen = Screen.Chats,
                tab = Tab.Chats,
                openSession = null,
                lines = emptyList(),
                attachments = emptyList(),
                sessionModel = null,
                sessionProvider = null,
                models = emptyList(),
                modelsProfile = null,
                modelsError = null,
                defaultModel = null,
                serverConfig = null,
                agentSettings = null,
                autoStart = null,
            )
        }
    }

    // ── conversation ──────────────────────────────────────────────────────

    /** Open an existing Studio conversation and load its history. */
    fun openSession(session: SessionSummary, focusMessageId: String? = session.matchedMessageId) {
        // Only one run is tracked at a time. If it belongs to the session being
        // opened, re-attach the live view; if it belongs to another session, leave
        // it running (cancelling the socket here is what killed both the stream
        // and the status when the user hopped between conversations).
        when {
            activeRunSessionId == session.id -> {
                attachLiveRun()
                // The run owns this session: ensure runSessionId is visible in state
                // so the header/status strip knows a run is in progress here.
                _state.update { it.copy(runSessionId = session.id) }
            }
            activeRunSessionId != null -> _state.update {
                it.copy(runStatus = "", runNotices = emptyList(), runStartedAtMillis = 0, runSessionId = null)
            }
            else -> cancelActiveRun(abort = false)
        }
        historyJob?.cancel()
        val profile = session.profile?.ifBlank { null }
            ?: _state.value.activeProfile.ifBlank { "default" }
        store.setSessionFor(profile, session.id)
        _state.update {
            val runtime = runtimeForSession(session)
            it.copy(
                screen = Screen.Conversation,
                openSession = session,
                sessionModel = session.model,
                sessionProvider = session.provider,
                contextTokens = -1,
                contextWindow = 0,
                loadingContext = true,
                lines = emptyList(),
                // Plans belong to the conversation that produced them; a session
                // without a plan must not inherit the previous one's card.
                taskPlans = emptyList(),
                attachments = emptyList(),
                models = if (it.modelsProfile == profile) it.models else emptyList(),
                modelsProfile = it.modelsProfile.takeIf { loaded -> loaded == profile },
                modelsError = if (it.modelsProfile == profile) it.modelsError else null,
                loadingHistory = true,
                error = null,
                notice = null,
                selectedRuntime = runtime,
                pendingRunAction = null,
                focusMessageId = focusMessageId,
            )
        }

        historyJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    // One page, not the whole transcript: this session has 572
                    // human messages and the unpaginated route returned all of them.
                    val page = api.conversationPage(session.id, limit = HISTORY_PAGE_SIZE, offset = 0)
                    val window = runCatching { api.contextLength(profile, session.provider, session.model) }.getOrDefault(0)
                    page to window
                }
            }
                .onSuccess { (page, window) ->
                    // A message from another device arrived while this phone was
                    // elsewhere; this load already contains it, so the debt is paid.
                    if (peerMessagePending) peerMessagePending = false
                    _state.update { state ->
                        if (state.screen != Screen.Conversation || state.openSession?.id != session.id) {
                            return@update state
                        }
                        state.copy(
                            loadingHistory = false,
                            loadingContext = false,
                            contextTokens = -1,
                            contextWindow = window,
                            historyTotal = page.total,
                            historyHasMore = page.hasMore,
                            historyOffset = page.offset + page.messages.size,
                            // The page carries the live plan, so the card is correct
                            // on open without a second socket.
                            taskPlans = page.taskPlans,
                            // Re-attaching a live run happens after this copy: the
                            // history above would otherwise erase the partial answer.
                            lines = messagesToLines(page.messages),
                        )
                    }
                }
                .also {
                    // Runs after both success and failure, so a live run is visible
                    // either way.
                    if (activeRunSessionId == session.id) attachLiveRun()
                }
                .onFailure { failure ->
                    if (failure is kotlinx.coroutines.CancellationException) return@onFailure
                    _state.update {
                        if (it.screen == Screen.Conversation && it.openSession?.id == session.id) {
                            it.copy(loadingHistory = false, loadingContext = false, error = failure.readableMessage(localized))
                        } else {
                            it
                        }
                    }
                }
        }
    }

    /** Key of the streaming bubble; never collides with a server message id. */
    private val LIVE_LINE_ID = "live"

    /** Older messages are fetched a page at a time, newest-first offsets. */
    private val HISTORY_PAGE_SIZE = 50
    private val HISTORY_PAGE_MAX = 500

    /**
     * Prepends one older page. The server counts offsets from the newest message,
     * so "older" means moving [historyOffset] forward.
     */
    fun loadOlderMessages() {
        val session = _state.value.openSession ?: return
        val snapshot = _state.value
        if (snapshot.loadingOlder || !snapshot.historyHasMore || snapshot.historyOffset >= HISTORY_PAGE_MAX) return
        _state.update { it.copy(loadingOlder = true, error = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.conversationPage(session.id, limit = HISTORY_PAGE_SIZE, offset = snapshot.historyOffset)
                }
            }
                .onSuccess { page ->
                    _state.update { state ->
                        if (state.openSession?.id != session.id || state.screen != Screen.Conversation) return@update state
                        // Guard against a double tap producing the same page twice.
                        val known = state.lines.mapNotNull { it.messageId }.toSet()
                        val older = messagesToLines(
                            page.messages.filter { it.id.isBlank() || it.id !in known },
                        )
                        state.copy(
                            loadingOlder = false,
                            historyHasMore = page.hasMore,
                            historyOffset = page.offset + page.messages.size,
                            lines = older + state.lines,
                        )
                    }
                }
                .onFailure { failure ->
                    if (failure is kotlinx.coroutines.CancellationException) return@onFailure
                    _state.update {
                        it.copy(loadingOlder = false, error = failure.readableMessage(localized))
                    }
                }
        }
    }


    /**
     * Reloads the open conversation without dropping the composer or flashing an
     * empty screen, keeping however much history is already on screen.
     */
    fun refreshConversation() {
        val session = _state.value.openSession ?: return
        historyJob?.cancel()
        _state.update { it.copy(loadingHistory = true, loadingContext = true, error = null) }
        val wanted = _state.value.historyOffset.coerceIn(HISTORY_PAGE_SIZE, HISTORY_PAGE_MAX)
        historyJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val profile = session.profile?.ifBlank { null } ?: currentProfile()
                    val page = api.conversationPage(session.id, limit = wanted, offset = 0)
                    val window = runCatching { api.contextLength(profile, session.provider, session.model) }.getOrDefault(0)
                    page to window
                }
            }
                .onSuccess { (page, window) ->
                    _state.update { state ->
                        if (state.screen != Screen.Conversation || state.openSession?.id != session.id) {
                            return@update state
                        }
                        val live = state.lines.filter { it.streaming && !it.fromUser }
                        state.copy(
                            loadingHistory = false,
                            loadingContext = false,
                            contextWindow = window.takeIf { it > 0 } ?: state.contextWindow,
                            historyTotal = page.total,
                            historyHasMore = page.hasMore,
                            historyOffset = page.offset + page.messages.size,
                            taskPlans = if (page.taskPlans.isNotEmpty()) page.taskPlans else state.taskPlans,
                            lines = messagesToLines(page.messages) + live,
                        )
                    }
                }
                .onFailure { failure ->
                    if (failure is kotlinx.coroutines.CancellationException) return@onFailure
                    _state.update {
                        if (it.screen == Screen.Conversation && it.openSession?.id == session.id) {
                            it.copy(loadingHistory = false, loadingContext = false, error = failure.readableMessage(localized))
                        } else {
                            it
                        }
                    }
                }
        }
    }

    fun startNewConversation(runtime: AgentRuntimeSelection = AgentRuntimeSelection()) {
        cancelActiveRun(abort = false)
        historyJob?.cancel()
        historyJob = null
        val profile = _state.value.activeProfile.ifBlank { "default" }
        store.setSessionFor(profile, "")
        _state.update {
            it.copy(
                screen = Screen.Conversation,
                openSession = null,
                lines = emptyList(),
                taskPlans = emptyList(),
                attachments = emptyList(),
                sessionModel = null,
                sessionProvider = null,
                contextTokens = -1,
                contextWindow = 0,
                loadingContext = false,
                models = if (it.modelsProfile == profile) it.models else emptyList(),
                modelsProfile = it.modelsProfile.takeIf { loaded -> loaded == profile },
                modelsError = if (it.modelsProfile == profile) it.modelsError else null,
                error = null,
                notice = null,
                selectedRuntime = runtime,
                pendingRunAction = null,
            )
        }
    }

    /** The "+" on the chat list: category and starting workspace for the new chat. */
    fun openNewChatSheet() = _state.update { it.copy(newChatSheetOpen = true, error = null) }

    fun closeNewChatSheet() = _state.update { it.copy(newChatSheetOpen = false) }

    fun setNewChatCategory(id: Int?) = _state.update { it.copy(newChatCategoryId = id) }

    fun setNewChatWorkspace(path: String?) =
        _state.update { it.copy(newChatWorkspace = path?.trim()?.ifBlank { null }) }

    /** Closes the sheet and starts the conversation; the choices ride along. */
    fun startNewConversationWithChoices() {
        _state.update { it.copy(newChatSheetOpen = false) }
        startNewConversation()
    }

    fun openAgentRuntimes() {
        _state.update { it.copy(screen = Screen.AgentRuntimes, loadingAgentRuntimes = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.agentRuntimes() } }
                .onSuccess { runtimes -> _state.update { it.copy(agentRuntimes = runtimes, loadingAgentRuntimes = false) } }
                .onFailure { failure ->
                    _state.update { it.copy(loadingAgentRuntimes = false, error = failure.readableMessage(localized)) }
                }
        }
    }

    fun startRuntimeConversation(runtime: AgentRuntimeStatus) {
        if (!runtime.installed) return
        startNewConversation(AgentRuntimeSelection(runtime.id, runtime.family, runtime.name))
    }

    /** Sends a generated Studio file to Android's public Downloads folder. */
    fun downloadChatFile(file: ChatFileLink, profile: String) {
        val application = getApplication<Application>()
        val destinationName = uniqueQueuedDownloadName(file.fileName)
        runCatching {
            val url = api.downloadUrl(file.path, file.fileName, profile.ifBlank { null })
            val extension = file.fileName.substringAfterLast('.', "").lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: "application/octet-stream"
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(file.fileName)
                .setDescription(str(R.string.download_description, profile.ifBlank { "default" }))
                .setMimeType(mime)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, destinationName)
            store.token.takeIf { it.isNotBlank() }
                ?.let { request.addRequestHeader("Authorization", "Bearer $it") }
            profile.takeIf { it.isNotBlank() }
                ?.let { request.addRequestHeader("X-Hermes-Profile", it) }
            val manager = application.getSystemService(DownloadManager::class.java)
                ?: error("DownloadManager unavailable")
            manager.enqueue(request)
        }.onSuccess {
            _state.update {
                it.copy(notice = str(R.string.download_started, destinationName), error = null)
            }
        }.onFailure { failure ->
            queuedDownloadNames.remove(destinationName)
            _state.update {
                it.copy(
                    error = str(R.string.download_failed, failure.readableMessage(localized)),
                    notice = null,
                )
            }
        }
    }

    fun send(message: String, replyTo: String? = null) {
        val text = message.trim()
        val files = _state.value.attachments
        val openId = _state.value.openSession?.id
        // `sending` is app-wide: a run in another conversation must not block this
        // one. Only a run that owns *this* conversation is busy.
        val busyHere = activeRunSessionId != null && (openId == null || activeRunSessionId == openId)
        if ((text.isEmpty() && files.isEmpty()) || busyHere) return
        val session = _state.value.openSession
        val profile = session?.profile?.ifBlank { null }
            ?: _state.value.activeProfile.ifBlank { "default" }
        val selectedModel = _state.value.sessionModel
        val selectedProvider = _state.value.sessionProvider
        val reasoningEffort = _state.value.reasoningEffort
        val wantsVoiceReply = _state.value.voiceReplyPending

        // Studio's own client names a conversation before it exists, which is
        // what lets the very first message belong to a session.
        val sessionId = store.sessionFor(profile).ifBlank { session?.id }
            ?: java.util.UUID.randomUUID().toString().also { store.setSessionFor(profile, it) }

        val echo = if (files.isEmpty()) text else {
            listOf(text.ifBlank { null }, files.joinToString(", ") { "📎 " + it.name })
                .filterNotNull()
                .joinToString("\n")
        }
        _state.update {
            it.copy(
                lines = it.lines + ChatLine(echo, fromUser = true, replyToNote = replyTo?.take(400)),
                sending = true,
                attachments = emptyList(),
                error = null,
                activity = null,
                runNotices = emptyList(),
                runStatus = "running",
                runStartedAtMillis = System.currentTimeMillis(),
                voiceReplyPending = false,
            )
        }

        // A run in another conversation keeps running server-side; we simply stop
        // tracking it here, so this conversation stays usable.
        if (activeRunSessionId != null && activeRunSessionId != sessionId) {
            cancelActiveRun(abort = false)
        }
        activeRunSessionId = sessionId
        _state.update { it.copy(runSessionId = sessionId) }
        runAnswer.setLength(0)
        runThinking.setLength(0)
        runStreamed = false
        runStartedAtMillis = System.currentTimeMillis()
        runJob = viewModelScope.launch {
            var finalReply = ""
            val answer = runAnswer
            val thinking = runThinking
            val runStartedAt = runStartedAtMillis

            // A run keeps going while the user reads another conversation. Its
            // events must not paint over whatever is on screen, so every UI
            // write is gated on this check; the buffers above still accumulate
            // and are replayed when the user comes back.
            fun onScreen(): Boolean =
                _state.value.screen == Screen.Conversation && _state.value.openSession?.id == sessionId

            fun ensureStreamingReply(startedAtMillis: Long = runStartedAt) {
                if (runStreamed) return
                runStreamed = true
                if (!onScreen()) return
                _state.update {
                    it.copy(
                        lines = it.lines + ChatLine(
                            text = "",
                            fromUser = false,
                            streaming = true,
                            startedAtMillis = startedAtMillis,
                            // Stable key: the live bubble must survive a history
                            // page being prepended above it.
                            messageId = LIVE_LINE_ID,
                        ),
                        activity = null,
                    )
                }
            }

            runCatching {
                chat.run(
                    profile = profile,
                    sessionId = sessionId,
                    input = replyTo?.let { quoteForReply(it, text) } ?: text,
                    attachments = files,
                    reasoningEffort = reasoningEffort,
                    model = selectedModel,
                    provider = selectedProvider,
                    runtime = _state.value.selectedRuntime,
                    cachedPageId = resumePageIds[sessionId]
                        ?: store.getResumePageId(profile.ifBlank { "default" }, sessionId),
                    // Only for a conversation that does not exist yet: the server's
                    // run handler reads category_id and workspace off the payload,
                    // which is the only way to file the very first message.
                    categoryId = _state.value.newChatCategoryId.takeIf { openId == null },
                    workspace = _state.value.newChatWorkspace.takeIf { openId == null },
                )
                    .collect { event ->
                        when (event) {
                            is RunEvent.Started -> {
                                if (onScreen()) _state.update {
                                    it.copy(runStatus = "running", runStartedAtMillis = event.occurredAtMillis)
                                }
                                // The session exists now, so the server has applied the
                                // choices. Clearing them stops the *next* new chat from
                                // silently inheriting this one's category.
                                if (openId == null && _state.value.newChatCategoryId != null) {
                                    _state.update { it.copy(newChatCategoryId = null) }
                                }
                                if (openId == null && _state.value.newChatWorkspace != null) {
                                    _state.update { it.copy(newChatWorkspace = null) }
                                }
                                ensureStreamingReply(event.occurredAtMillis)
                            }
                            is RunEvent.Status -> if (onScreen()) _state.update {
                                it.copy(
                                    runStatus = event.status,
                                    runStartedAtMillis = if (event.status == "running") {
                                        it.runStartedAtMillis.takeIf { t -> t > 0 } ?: System.currentTimeMillis()
                                    } else {
                                        it.runStartedAtMillis
                                    },
                                )
                            }
                            is RunEvent.Notice -> if (onScreen()) {
                                // Keep the tail: a long retry storm must not grow the
                                // state without bound, and only the last line matters.
                                _state.update {
                                    it.copy(runNotices = (it.runNotices + event.text).takeLast(6))
                                }
                                ensureStreamingReply()
                            }
                            is RunEvent.Plans -> if (onScreen()) {
                                // The event carries the plan for this run's session;
                                // off-screen it is re-read from history on return.
                                _state.update { it.copy(taskPlans = event.plans) }
                            }
                            is RunEvent.TitleChanged -> _state.update { state ->
                                // The agent names the conversation itself; the list and
                                // the header should follow without a manual refresh.
                                val open = state.openSession
                                state.copy(
                                    openSession = if (open != null && open.id == sessionId) {
                                        open.copy(title = event.title)
                                    } else {
                                        open
                                    },
                                    sessions = state.sessions.map {
                                        if (it.id == sessionId) it.copy(title = event.title) else it
                                    },
                                )
                            }
                            is RunEvent.Compression -> _state.update {
                                it.copy(
                                    activity = str(
                                        if (event.running) R.string.compression_running
                                        else R.string.compression_done,
                                    ),
                                    // Only the numbers are kept; the UI decides the wording.
                                    compression = CompressionUi(
                                        running = event.running,
                                        messageCount = event.messageCount,
                                        beforeTokens = event.beforeTokens,
                                        afterTokens = event.afterTokens,
                                    ),
                                )
                            }
                            is RunEvent.Interim -> {
                                // A whole message at once: it replaces whatever the
                                // streaming bubble was showing, and is not streaming.
                                answer.clear()
                                answer.append(event.text)
                                runStreamed = true
                                if (onScreen()) {
                                    ensureStreamingReply()
                                    updateLastReply(event.text, thinking.toString(), streaming = false)
                                }
                            }
                            is RunEvent.ReasoningAvailable -> if (onScreen()) {
                                // The fold exists even if the first delta is still in
                                // flight; opening the bubble now shows "思考中" instead
                                // of looking like the model said nothing.
                                ensureStreamingReply()
                            }
                            is RunEvent.RunningSessions -> _state.update {
                                it.copy(remoteRunningSessions = event.ids.toSet())
                            }
                            is RunEvent.WorkspaceChanged -> _state.update { state ->
                                state.copy(
                                    openSession = state.openSession?.let { open ->
                                        if (open.id == sessionId) open.copy(workspace = event.path) else open
                                    },
                                    sessions = state.sessions.map { row ->
                                        if (row.id == sessionId) row.copy(workspace = event.path) else row
                                    },
                                )
                            }
                            is RunEvent.WorkspaceDiff -> {
                                // The change set is fetched from history on return; the
                                // banner only needs to say that something changed.
                                _state.update { state ->
                                    state.copy(
                                        runNotices = (state.runNotices + str(R.string.workspace_changed_files, event.files)).takeLast(6),
                                    )
                                }
                            }
                            is RunEvent.PeerMessage -> {
                                // Another device wrote into this conversation. Reload,
                                // but quietly: a spinner here would erase the reply the
                                // user is reading while it is still arriving.
                                if (onScreen()) refreshConversationQuietly() else peerMessagePending = true
                            }
                            is RunEvent.CompletionNotice -> _state.update { state ->
                                // An in-app notice for runs this phone did not start;
                                // the system notification is a separate permission.
                                state.copy(
                                    notice = event.content.ifBlank { event.title },
                                    remoteRunningSessions = state.remoteRunningSessions - event.sessionId,
                                )
                            }
                            is RunEvent.AbortPhase -> when (event.phase) {
                                "started" -> _state.update { it.copy(activity = str(R.string.run_stopping)) }
                                // "timeout" means the server did NOT stop. Saying
                                // "stopped" there would be a lie.
                                "timeout" -> _state.update {
                                    it.copy(
                                        activity = null,
                                        error = str(R.string.run_stop_timeout),
                                        runStatus = "running",
                                    )
                                }
                                else -> _state.update { it.copy(activity = null) }
                            }
                            is RunEvent.Text -> {
                                answer.append(event.delta)
                                if (onScreen()) {
                                    ensureStreamingReply()
                                    updateLastReply(answer.toString(), thinking.toString(), streaming = true)
                                }
                            }
                            is RunEvent.Reasoning -> {
                                thinking.append(event.delta)
                                if (onScreen()) {
                                    ensureStreamingReply()
                                    updateLastReply(answer.toString(), thinking.toString(), streaming = true)
                                }
                            }
                            is RunEvent.Tool -> if (onScreen()) {
                                ensureStreamingReply()
                                updateLastTool(event)
                            }
                            is RunEvent.Usage -> if (onScreen()) _state.update {
                                it.copy(
                                    contextTokens = event.contextTokens,
                                    contextWindow = event.contextWindow ?: it.contextWindow,
                                )
                            }
                            is RunEvent.Done -> {
                                val output = event.output.ifBlank { answer.toString() }
                                finalReply = output
                                val reasoning = event.reasoning.ifBlank { thinking.toString() }
                                if (!onScreen()) {
                                    // Nothing to paint: finishRun() reloads the
                                    // transcript from the server once the user is
                                    // back on this conversation.
                                } else if (runStreamed) {
                                    updateLastReply(output, reasoning, streaming = false)
                                } else {
                                    _state.update {
                                        it.copy(
                                            lines = it.lines + ChatLine(
                                                text = output,
                                                fromUser = false,
                                                reasoning = reasoning.ifBlank { null },
                                            ),
                                        )
                                    }
                                }
                                runStreamed = true
                            }
                            is RunEvent.RequiresAction -> {
                                            // Always record the action so the user can answer it later,
                                            // even if they've navigated away. The UI will decide whether to
                                            // show it inline (same session) or as a dialog (other screen).
                                            _state.update {
                                                it.copy(
                                                    pendingRunAction = PendingRunAction(event.kind, event.id, event.prompt, event.options, sessionId),
                                                    activity = null,
                                                )
                                            }
                                            if (!onScreen()) {
                                                runStreamed = true
                                            } else if (runStreamed) {
                                                updateLastReply(answer.toString(), thinking.toString(), streaming = false)
                                            }
                                            runStreamed = true
                                        }
                            is RunEvent.ActionResolved -> _state.update { state ->
                                if (state.pendingRunAction?.id != event.id) state
                                else if (event.resolved) state.copy(pendingRunAction = null, activity = null)
                                else state.copy(activity = null)
                            }
                            is RunEvent.QueueChanged -> _state.update {
                                it.copy(
                                    queuedRuns = event.messages ?: it.queuedRuns,
                                    queueInsertionActive = event.insertionActive ?: it.queueInsertionActive,
                                )
                            }
                            is RunEvent.BackgroundAgent -> _state.update { state ->
                                val tasks = state.backgroundAgentRuns.toMutableList()
                                val index = tasks.indexOfFirst { it.id == event.task.id }
                                if (index >= 0) tasks[index] = event.task else tasks += event.task
                                state.copy(backgroundAgentRuns = tasks)
                            }
                            is RunEvent.ResumedState -> {
                                event.pageId?.let { pageId ->
                                    resumePageIds[sessionId] = pageId
                                    store.setResumePageId(profile.ifBlank { "default" }, sessionId, pageId)
                                }
                                _state.update { state ->
                                    val restoredLines = event.messages?.mapNotNull { message ->
                                        when (message.role) {
                                            "user", "command" -> ChatLine(message.content, fromUser = true, messageId = message.id)
                                            "assistant" -> ChatLine(message.content, fromUser = false, reasoning = message.reasoning, messageId = message.id)
                                            else -> null
                                        }
                                    }
                                    state.copy(
                                        lines = restoredLines ?: state.lines,
                                        taskPlans = event.taskPlans,
                                        sessionModel = event.model ?: state.sessionModel,
                                        sessionProvider = event.provider ?: state.sessionProvider,
                                        reasoningEffort = event.reasoningEffort ?: state.reasoningEffort,
                                        openSession = state.openSession?.let { open ->
                                            if (open.id == sessionId && event.workspace != null) open.copy(workspace = event.workspace) else open
                                        },
                                        workspaceRunChanges = event.workspaceChanges.ifEmpty { state.workspaceRunChanges },
                                    )
                                }
                            }
                            is RunEvent.Failed -> {
                                // A socket that never got going is not a failed
                                // run: fall back to the REST wrapper instead of
                                // telling the user the answer is lost.
                                if (event.retryableTransport && !runStreamed) throw SocketUnavailable(event.error)
                                updateLastReply(
                                    answer.toString(),
                                    thinking.toString(),
                                    streaming = false,
                                    terminalToolStatus = ToolRunStatus.Error,
                                )
                                if (onScreen()) _state.update {
                                    it.copy(lines = it.lines + ChatLine(event.error, fromUser = false, isError = true))
                                }
                            }
                        }
                    }
            }.onFailure { failure ->
                if (failure is kotlinx.coroutines.CancellationException) return@onFailure
                if (failure is SocketUnavailable) {
                    sendOverRest(
                        profile = profile,
                        sessionId = sessionId,
                        text = text,
                        files = files,
                        reasoningEffort = reasoningEffort,
                        model = selectedModel,
                        provider = selectedProvider,
                        runtime = _state.value.selectedRuntime,
                        speakReply = wantsVoiceReply,
                    )
                    finishRun(sessionId)
                    return@launch
                }
                _state.update {
                    it.copy(
                        lines = it.lines + ChatLine(failure.readableMessage(localized), fromUser = false, isError = true),
                    )
                }
            }
            if (wantsVoiceReply && finalReply.isNotBlank()) speak(finalReply, profile)
            finishRun(sessionId)
        }
    }

    fun resolveRunAction(response: String) {
        val action = _state.value.pendingRunAction ?: return
        if (response.isBlank()) return
        when (action.kind) {
            RequiredAction.Approval -> chat.respondToApproval(action.sessionId, action.id, response)
            RequiredAction.Clarification -> chat.respondToClarification(action.sessionId, action.id, response)
        }
        _state.update { it.copy(activity = str(R.string.run_action_resuming)) }
    }

    fun insertQueuedRun(queueId: String) {
        val sessionId = activeRunSessionId ?: _state.value.openSession?.id ?: return
        chat.insertQueuedRun(sessionId, queueId)
    }

    fun cancelQueuedRun(queueId: String) {
        val sessionId = activeRunSessionId ?: _state.value.openSession?.id ?: return
        chat.cancelQueuedRun(sessionId, queueId)
    }

    /** Older servers, or a blocked WebSocket, still answer over plain HTTP. */
    private suspend fun sendOverRest(
        profile: String,
        sessionId: String,
        text: String,
        files: List<Upload>,
        reasoningEffort: String?,
        model: String?,
        provider: String?,
        runtime: AgentRuntimeSelection,
        speakReply: Boolean = false,
    ) {
        runCatching {
            withContext(Dispatchers.IO) {
                api.sendMessage(
                    profile = profile,
                    input = text,
                    sessionId = sessionId,
                    attachments = files,
                    reasoningEffort = reasoningEffort,
                    model = model,
                    provider = provider,
                    runtime = runtime,
                )
            }
        }.onSuccess { reply ->
            if (activeRunSessionId != sessionId) return@onSuccess
            reply.sessionId?.let { store.setSessionFor(profile, it) }
            val line = if (reply.error != null && reply.output.isBlank()) {
                ChatLine(reply.error, fromUser = false, isError = true)
            } else {
                ChatLine(reply.output, fromUser = false, reasoning = reply.reasoning)
            }
            _state.update { it.copy(lines = it.lines + line, sending = false, activity = null) }
            if (speakReply && !line.isError && line.text.isNotBlank()) speak(line.text, profile)
        }.onFailure { failure ->
            if (failure is kotlinx.coroutines.CancellationException || activeRunSessionId != sessionId) {
                return@onFailure
            }
            _state.update {
                it.copy(
                    lines = it.lines + ChatLine(failure.readableMessage(localized), fromUser = false, isError = true),
                    sending = false,
                    activity = null,
                )
            }
        }
    }

    private fun runtimeForSession(session: SessionSummary): AgentRuntimeSelection {
        val id = when (session.agentId?.lowercase()) {
            "ekko", "ekko-agent" -> "ekko-agent"
            "claude", "claude-code" -> "claude-code"
            "codex" -> "codex"
            "pi" -> "pi"
            else -> "hermes"
        }
        val family = when (id) { "hermes" -> "hermes"; "ekko-agent" -> "ekko"; else -> "coding" }
        val global = session.source == "global_agent"
        val name = if (global) "Global Agent" else when (id) { "hermes" -> "Hermes"; "ekko-agent" -> "Ekko"; "claude-code" -> "Claude Code"; "codex" -> "Codex"; else -> "Pi" }
        return AgentRuntimeSelection(id, family, name, global)
    }

    private fun updateLastReply(
        text: String,
        reasoning: String,
        streaming: Boolean,
        terminalToolStatus: ToolRunStatus = ToolRunStatus.Done,
    ) {
        _state.update { state ->
            val lines = state.lines.toMutableList()
            val index = lines.indexOfLast { !it.fromUser && !it.isError }
            if (index < 0) return@update state
            val now = System.currentTimeMillis()
            val current = lines[index]
            if (!streaming && text.isBlank() && reasoning.isBlank() && current.tools.isEmpty()) {
                lines.removeAt(index)
                return@update state.copy(lines = lines)
            }
            lines[index] = lines[index].copy(
                text = text,
                reasoning = reasoning.ifBlank { null },
                streaming = streaming,
                finishedAtMillis = if (streaming) null else now,
                tools = if (streaming) {
                    current.tools
                } else {
                    current.tools.map { tool ->
                        if (tool.status != ToolRunStatus.Running) tool else tool.copy(
                            status = terminalToolStatus,
                            durationSeconds = tool.durationSeconds
                                ?: ((now - tool.startedAtMillis).coerceAtLeast(0) / 1000.0),
                        )
                    }
                },
            )
            state.copy(lines = lines)
        }
    }

    private fun updateLastTool(event: RunEvent.Tool) {
        _state.update { state ->
            val lines = state.lines.toMutableList()
            val lineIndex = lines.indexOfLast { !it.fromUser && !it.isError }
            if (lineIndex < 0) return@update state
            val line = lines[lineIndex]
            val tools = line.tools.toMutableList()
            val matchingIndex = when {
                event.id.isNotBlank() -> tools.indexOfLast { it.id == event.id }
                event.status != ToolRunStatus.Running -> tools.indexOfLast {
                    it.status == ToolRunStatus.Running && it.name == event.name
                }
                else -> -1
            }

            if (matchingIndex >= 0) {
                val current = tools[matchingIndex]
                tools[matchingIndex] = current.copy(
                    name = event.name.ifBlank { current.name },
                    detail = current.detail ?: event.detail,
                    status = event.status,
                    durationSeconds = event.durationSeconds ?: if (event.status == ToolRunStatus.Running) {
                        current.durationSeconds
                    } else {
                        (event.occurredAtMillis - current.startedAtMillis).coerceAtLeast(0) / 1000.0
                    },
                )
            } else {
                val fallbackDuration = event.durationSeconds
                val startedAt = if (fallbackDuration != null) {
                    event.occurredAtMillis - (fallbackDuration * 1000).toLong()
                } else {
                    event.occurredAtMillis
                }
                tools += ChatToolStep(
                    id = event.id.ifBlank { "${event.name}-${event.occurredAtMillis}" },
                    name = event.name,
                    detail = event.detail,
                    status = event.status,
                    startedAtMillis = startedAt,
                    durationSeconds = fallbackDuration,
                )
            }

            lines[lineIndex] = line.copy(tools = tools)
            val active = tools.lastOrNull { it.status == ToolRunStatus.Running }?.name
            state.copy(lines = lines, activity = active)
        }
    }

    /** Asks the server to stop the run that is streaming right now. */
    fun stopRun() {
        cancelActiveRun(abort = true)
    }

    /**
     * Reload the transcript without the loading state.
     *
     * `refreshConversation()` sets `loadingHistory`, which replaces the list with
     * a spinner and loses the scroll position. That is right when the user asks
     * for a refresh and wrong when the server pushes a message from another
     * device mid-read.
     */
    fun refreshConversationQuietly() {
        val session = _state.value.openSession ?: return
        val wanted = _state.value.historyOffset.coerceIn(HISTORY_PAGE_SIZE, HISTORY_PAGE_MAX)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.conversationPage(session.id, limit = wanted, offset = 0) }
            }.onSuccess { page ->
                _state.update { state ->
                    if (state.screen != Screen.Conversation || state.openSession?.id != session.id) return@update state
                    val live = state.lines.filter { it.streaming && !it.fromUser }
                    state.copy(
                        historyTotal = page.total,
                        historyHasMore = page.hasMore,
                        historyOffset = page.offset + page.messages.size,
                        lines = messagesToLines(page.messages) + live,
                    )
                }
            }
        }
    }

    private fun cancelActiveRun(abort: Boolean) {
        val sessionId = activeRunSessionId
        if (abort && !sessionId.isNullOrBlank()) chat.abort(sessionId)
        runJob?.cancel()
        runJob = null
        activeRunSessionId = null
        _state.update { state ->
            val lines = state.lines.toMutableList()
            val index = lines.indexOfLast { it.streaming }
            if (index >= 0) {
                val now = System.currentTimeMillis()
                lines[index] = lines[index].copy(
                    streaming = false,
                    finishedAtMillis = now,
                    tools = lines[index].tools.map { tool ->
                        if (tool.status != ToolRunStatus.Running) tool else tool.copy(
                            status = ToolRunStatus.Error,
                            durationSeconds = (now - tool.startedAtMillis).coerceAtLeast(0) / 1000.0,
                        )
                    },
                )
            }
            state.copy(lines = lines, sending = false, activity = null)
        }
    }

    /**
     * Re-binds the on-screen conversation to the run that is already in flight.
     *
     * Idempotent: any previous live bubble is replaced, so it can be called both
     * when entering the conversation and after the history reload finishes
     * (history would otherwise wipe the partial answer).
     */
    private fun attachLiveRun() {
        val sessionId = activeRunSessionId ?: return
        _state.update { state ->
            if (state.openSession?.id != sessionId) return@update state
            val withoutLive = state.lines.filterNot { it.streaming && !it.fromUser }
            state.copy(
                sending = true,
                runStatus = "running",
                runStartedAtMillis = runStartedAtMillis,
                lines = withoutLive + ChatLine(
                    text = runAnswer.toString(),
                    fromUser = false,
                    streaming = true,
                    startedAtMillis = runStartedAtMillis,
                    messageId = LIVE_LINE_ID,
                ),
            )
        }
        runStreamed = true
    }

    private fun finishRun(sessionId: String) {
        if (activeRunSessionId != sessionId) return
        activeRunSessionId = null
        runJob = null
        _state.update {
            it.copy(
                sending = false,
                activity = null,
                runStatus = "",
                runStartedAtMillis = 0,
                runSessionId = null,
            )
        }
        // The run finished while another conversation was open: its messages only
        // exist on the server, so pull them in when the user is on this screen.
        // Otherwise the list stays stale until the next refresh, which is harmless.
        if (_state.value.screen == Screen.Conversation && _state.value.openSession?.id == sessionId) {
            refreshConversation()
        }
    }

    private class SocketUnavailable(message: String) : Exception(message)

    // ── attachments ───────────────────────────────────────────────────────

    /** Uploads the picked file to the server so the agent can read it by path. */
    fun attach(bytes: ByteArray, filename: String, mime: String) {
        // The official client caps a message at 9 files of 20 MB each; the
        // server does not, so an unbounded list would only fail later.
        val attached = _state.value.attachments.size
        if (attached >= MAX_ATTACHMENTS) {
            _state.update { it.copy(error = str(R.string.attach_limit_count, MAX_ATTACHMENTS)) }
            return
        }
        if (bytes.size > MAX_ATTACHMENT_BYTES) {
            _state.update { it.copy(error = str(R.string.attach_limit_size, MAX_ATTACHMENT_BYTES / 1_048_576)) }
            return
        }
        val profile = currentProfile()
        _state.update { it.copy(attaching = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.upload(profile, bytes, filename, mime) } }
                .onSuccess { upload ->
                    _state.update { it.copy(attaching = false, attachments = it.attachments + upload) }
                }
                .onFailure { failure ->
                    _state.update { it.copy(attaching = false, error = failure.readableMessage(localized)) }
                }
        }
    }

    fun removeAttachment(upload: Upload) {
        _state.update { it.copy(attachments = it.attachments - upload) }
    }

    // ── voice ─────────────────────────────────────────────────────────────

    fun startRecording() {
        if (_state.value.recording) return
        if (recorder.start()) {
            _state.update { it.copy(recording = true, error = null) }
        } else {
            _state.update { it.copy(error = str(R.string.error_microphone)) }
        }
    }

    fun cancelRecording() {
        recorder.cancel()
        _state.update { it.copy(recording = false) }
    }

    /** Stops the take and turns it into text with the profile's STT provider. */
    fun stopRecordingAndTranscribe() {
        if (!_state.value.recording) return
        val bytes = recorder.stop()
        _state.update { it.copy(recording = false) }
        if (bytes == null) {
            _state.update { it.copy(error = str(R.string.error_recording_short)) }
            return
        }

        val profile = currentProfile()
        _state.update { it.copy(transcribing = true, error = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.transcribe(profile, bytes, "voice.m4a", "audio/mp4")
                }
            }.onSuccess { text ->
                _state.update { it.copy(transcribing = false, transcript = text, voiceReplyPending = true) }
            }.onFailure { failure ->
                _state.update { it.copy(transcribing = false, error = failure.readableMessage(localized)) }
            }
        }
    }

    /** The transcript has been scrolled to the search hit; forget the target. */
    fun consumeFocusMessage() = _state.update { it.copy(focusMessageId = null) }

    fun consumeTranscript() = _state.update { it.copy(transcript = null) }

    /**
     * Reads a chat attachment as text for the previewer.
     *
     * The server route is the same one images use; only the decoding differs, so
     * there is no second endpoint to keep in step.
     */
    suspend fun readChatFileText(path: String): String? = withContext(Dispatchers.IO) {
        runCatching { api.fetchImageBytes(path)?.toString(Charsets.UTF_8) }.getOrNull()
    }

    /** Installed into [LocalImageFetcher] so markdown images can authenticate. */
    suspend fun fetchImage(url: String): ByteArray? =
        withContext(Dispatchers.IO) { runCatching { api.fetchImageBytes(url) }.getOrNull() }

    /** A short confirmation after a copy; the clipboard gives no feedback itself. */
    fun notifyCopied() = _state.update { it.copy(notice = str(R.string.message_copied)) }

    fun stopSpeaking() {
        runCatching { speechPlayer?.stop() }
        runCatching { speechPlayer?.release() }
        speechPlayer = null
        _state.update { it.copy(speaking = false, speakingMessageId = null) }
    }

    /**
     * Reads one message aloud, or stops it if it is already playing.
     *
     * Tapping the same bubble twice is the natural "stop", so it is handled here
     * instead of needing a separate control.
     */
    fun speakLine(line: ChatLine) {
        val id = line.messageId
        if (_state.value.speaking && id != null && _state.value.speakingMessageId == id) {
            stopSpeaking()
            return
        }
        if (line.text.isBlank()) return
        val profile = _state.value.openSession?.profile?.ifBlank { null }
            ?: _state.value.activeProfile.ifBlank { "default" }
        _state.update { it.copy(speakingMessageId = id) }
        speak(line.text, profile)
    }

    private fun speak(text: String, profile: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val audio = api.synthesize(profile, text)
                    val file = java.io.File.createTempFile("hermes-reply-", audio.extension, getApplication<Application>().cacheDir)
                    file.writeBytes(audio.bytes)
                    file
                }
            }.onSuccess { file ->
                runCatching {
                    stopSpeaking()
                    MediaPlayer().also { player ->
                        speechPlayer = player
                        player.setDataSource(file.absolutePath)
                        player.setOnPreparedListener { ready ->
                            runCatching { ready.start() }
                                .onSuccess { _state.update { it.copy(speaking = true) } }
                                .onFailure { stopSpeaking(); file.delete(); _state.update { it.copy(error = it.error ?: str(R.string.voice_playback_failed)) } }
                        }
                        player.setOnCompletionListener { finished ->
                            runCatching { finished.release() }
                            if (speechPlayer === finished) speechPlayer = null
                            file.delete()
                            _state.update { it.copy(speaking = false, speakingMessageId = null) }
                        }
                        player.setOnErrorListener { failed, _, _ ->
                            runCatching { failed.release() }
                            if (speechPlayer === failed) speechPlayer = null
                            file.delete()
                            _state.update { it.copy(speaking = false, speakingMessageId = null, error = str(R.string.voice_playback_failed)) }
                            true
                        }
                        player.prepareAsync()
                    }
                }.onFailure {
                    stopSpeaking()
                    file.delete()
                    _state.update { state -> state.copy(error = str(R.string.voice_playback_failed)) }
                }
            }.onFailure { failure ->
                _state.update { it.copy(speaking = false, speakingMessageId = null, error = failure.readableMessage(localized)) }
            }
        }
    }

    override fun onCleared() {
        stopSpeaking()
        super.onCleared()
    }

    // ── model and reasoning ───────────────────────────────────────────────

    fun loadModels(force: Boolean = false) {
        val profile = currentProfile()
        val current = _state.value
        if (!force && current.modelsProfile == profile && (current.models.isNotEmpty() || current.loadingModels)) return
        _state.update {
            it.copy(
                models = if (it.modelsProfile == profile) it.models else emptyList(),
                modelsProfile = profile,
                loadingModels = true,
                modelsError = null,
            )
        }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.availableModels(profile) } }
                .onSuccess { models ->
                    _state.update {
                        if (it.modelsProfile == profile) it.copy(models = models, loadingModels = false, modelsError = null) else it
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        if (it.modelsProfile == profile) {
                            it.copy(
                                loadingModels = false,
                                modelsError = failure.readableMessage(localized),
                            )
                        } else {
                            it
                        }
                    }
                }
        }
    }

    /** Applies a model to the open session, or remembers it for the next one. */
    fun selectModel(option: ModelOption) {
        val sessionId = _state.value.openSession?.id
            ?: store.sessionFor(currentProfile()).ifBlank { null }
        _state.update {
            it.copy(
                sessionModel = option.id,
                sessionProvider = option.provider,
                loadingContext = true,
            )
        }
        viewModelScope.launch {
            val profile = currentProfile()
            val length = runCatching {
                withContext(Dispatchers.IO) { api.contextLength(profile, option.provider, option.id) }
            }.getOrDefault(0)
            _state.update {
                if (it.sessionModel == option.id && it.sessionProvider == option.provider) {
                    it.copy(
                        contextWindow = length.takeIf { value -> value > 0 } ?: it.contextWindow,
                        loadingContext = false,
                    )
                } else it
            }
        }
        if (sessionId == null) return

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.setSessionModel(sessionId, option.id, option.provider) }
            }.onFailure { failure ->
                _state.update { it.copy(error = failure.readableMessage(localized)) }
            }
        }
    }

    fun setReasoningEffort(effort: String) {
        store.reasoningEffort = effort
        _state.update { it.copy(reasoningEffort = effort) }
    }

    // ── group room ────────────────────────────────────────────────────────

    fun openRoom(room: Room) {
        leaveRoom()
        openingRoomId = room.id
        _state.update {
            it.copy(screen = Screen.Room, openRoom = null, loadingHistory = true, error = null, roomLive = false)
        }
        roomLoadJob = viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.room(room.id) } }
                .onSuccess { detail ->
                    if (_state.value.screen != Screen.Room || openingRoomId != room.id) return@onSuccess
                    _state.update { it.copy(openRoom = detail, loadingHistory = false) }
                    listenToRoom(room.id)
                }
                .onFailure { failure ->
                    if (failure is kotlinx.coroutines.CancellationException) return@onFailure
                    _state.update {
                        if (it.screen == Screen.Room && openingRoomId == room.id) {
                            it.copy(loadingHistory = false, error = failure.readableMessage(localized))
                        } else {
                            it
                        }
                    }
                }
        }
    }

    /** Keeps the open room current, and is what makes posting possible. */
    private fun listenToRoom(roomId: String) {
        roomJob?.cancel()
        roomJob = viewModelScope.launch {
            runCatching {
                group.join(roomId, _state.value.account ?: "phone").collect { event ->
                    when (event) {
                        is RoomEvent.Connected -> _state.update { it.copy(roomLive = true) }
                        is RoomEvent.Dropped -> _state.update { it.copy(roomLive = false) }
                        is RoomEvent.Failed -> {
                            _state.update {
                                it.copy(roomLive = false, error = event.error)
                            }
                        }
                        is RoomEvent.Posted -> _state.update { state ->
                            val room = state.openRoom ?: return@update state
                            if (room.id != roomId) return@update state
                            if (room.messages.any { it.id == event.message.id }) return@update state
                            state.copy(openRoom = room.copy(messages = room.messages + event.message))
                        }
                    }
                }
            }
        }
    }

    fun leaveRoom() {
        openingRoomId = null
        roomLoadJob?.cancel()
        roomLoadJob = null
        roomJob?.cancel()
        roomJob = null
        _state.update { it.copy(roomLive = false) }
    }

    // ── conversations ─────────────────────────────────────────────────────

    fun renameSession(session: SessionSummary, title: String) {
        val clean = title.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.renameSession(session.id, clean) } }
                .onSuccess {
                    _state.update { state ->
                        state.copy(
                            sessions = state.sessions.map {
                                if (it.id == session.id) it.copy(title = clean) else it
                            },
                            openSession = state.openSession?.takeIf { it.id == session.id }?.copy(title = clean)
                                ?: state.openSession,
                        )
                    }
                }
                .onFailure { failure -> _state.update { it.copy(error = failure.readableMessage(localized)) } }
        }
    }

    fun deleteSession(session: SessionSummary) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.deleteSession(session.id) } }
                .onSuccess {
                    // Forget the pointer too, or the next message would try to
                    // continue a conversation the server no longer has.
                    session.profile?.let { profile ->
                        if (store.sessionFor(profile) == session.id) store.setSessionFor(profile, "")
                    }
                    _state.update { state ->
                        state.copy(sessions = state.sessions.filterNot { it.id == session.id })
                    }
                }
                .onFailure { failure -> _state.update { it.copy(error = failure.readableMessage(localized)) } }
        }
    }

    // ── profiles ──────────────────────────────────────────────────────────

    fun createProfile(name: String) = profileWork(name) { api.createProfile(it) }

    fun renameProfile(from: String, to: String) = profileWork(to) { api.renameProfile(from, it) }

    fun deleteProfile(name: String) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.deleteProfile(name) } }
                .onSuccess {
                    if (store.profile == name) store.profile = ""
                    refreshProfiles()
                    _state.update { it.copy(notice = str(R.string.notice_profile_deleted, name)) }
                }
                .onFailure { failure -> _state.update { it.copy(error = failure.readableMessage(localized)) } }
        }
    }

    private fun profileWork(name: String, block: suspend (String) -> Unit) {
        val clean = name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { block(clean) } }
                .onSuccess { refreshProfiles() }
                .onFailure { failure -> _state.update { it.copy(error = failure.readableMessage(localized)) } }
        }
    }

    // ── rooms ─────────────────────────────────────────────────────────────

    fun createRoom(name: String, agents: List<String>) {
        val clean = name.trim()
        if (clean.isBlank()) return
        // Studio requires an invite code; one the user never has to think about
        // is better than a field they have to fill in.
        val code = (1..6).map { INVITE_ALPHABET.random() }.joinToString("")
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.createRoom(clean, code, agents) } }
                .onSuccess {
                    _state.update { it.copy(busy = false, notice = str(R.string.notice_room_created, clean)) }
                    refreshRooms()
                }
                .onFailure { failure ->
                    _state.update { it.copy(busy = false, error = failure.readableMessage(localized)) }
                }
        }
    }

    fun deleteRoom(room: Room) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.deleteRoom(room.id) } }
                .onSuccess {
                    _state.update { state -> state.copy(rooms = state.rooms.filterNot { it.id == room.id }) }
                }
                .onFailure { failure -> _state.update { it.copy(error = failure.readableMessage(localized)) } }
        }
    }

    // ── group agents ──────────────────────────────────────────────────────

    fun addRoomAgent(roomId: String, draft: RoomAgentDraft) = launchWork(
        work = { api.addRoomAgent(roomId, draft) },
        onSuccess = { agents -> applyRoomAgents(roomId, agents) },
    )

    fun updateRoomAgent(roomId: String, agent: RoomAgent, draft: RoomAgentDraft) = launchWork(
        work = { api.updateRoomAgent(roomId, agent, draft) },
        onSuccess = { agents -> applyRoomAgents(roomId, agents) },
    )

    fun deleteRoomAgent(roomId: String, agentId: String) = launchWork(
        work = { api.deleteRoomAgent(roomId, agentId) },
        onSuccess = { agents -> applyRoomAgents(roomId, agents) },
    )

    /**
     * The agent endpoints answer with the room's whole agent list, so the screen
     * updates from the response instead of firing a second read.
     */
    private fun applyRoomAgents(roomId: String, agents: List<RoomAgent>) {
        _state.update { state ->
            val room = state.openRoom
            if (room == null || room.id != roomId) return@update state
            state.copy(
                openRoom = room.copy(
                    agentList = agents,
                    agents = agents.map { it.name },
                ),
            )
        }
    }

    // ── members ───────────────────────────────────────────────────────────

    fun removeRoomMember(roomId: String, member: RoomMember) = launchWork(
        work = { api.removeRoomMember(roomId, member.userId) },
        onSuccess = { reopenRoom(roomId) },
    )

    // ── room settings ─────────────────────────────────────────────────────

    fun regenerateInviteCode(roomId: String) = launchWork(
        work = { api.regenerateInviteCode(roomId) },
        onSuccess = { code ->
            _state.update { state ->
                state.openRoom?.takeIf { it.id == roomId }
                    ?.let { state.copy(openRoom = it.copy(inviteCode = code)) }
                    ?: state
            }
        },
    )

    fun saveRoomConfig(
        roomId: String,
        name: String? = null,
        handoffEnabled: Boolean? = null,
        handoffUnlimited: Boolean? = null,
        handoffMaxDepth: Int? = null,
        summaryEveryTurns: Int? = null,
        summaryModel: String? = null,
        summaryProvider: String? = null,
        summaryProfile: String? = null,
    ) = launchWork(
        work = {
            api.updateRoomConfig(
                roomId = roomId,
                name = name,
                handoffEnabled = handoffEnabled,
                handoffUnlimited = handoffUnlimited,
                handoffMaxDepth = handoffMaxDepth,
                summaryEveryTurns = summaryEveryTurns,
                summaryModel = summaryModel,
                summaryProvider = summaryProvider,
                summaryProfile = summaryProfile,
            )
        },
        onSuccess = { reopenRoom(roomId) },
    )

    fun clearRoomContext(roomId: String) = launchWork(
        work = { api.clearRoomContext(roomId) },
        onSuccess = {
            _state.update { it.copy(notice = str(R.string.room_context_cleared)) }
            reopenRoom(roomId)
        },
    )

    // ── room workspace files ─────────────────────────────────────────────

    fun openRoomFiles() {
        _state.update { it.copy(screen = Screen.RoomFiles) }
        loadRoomFiles()
    }

    fun loadRoomFiles(path: String = _state.value.roomFilesPath) {
        val roomId = _state.value.openRoom?.id ?: return
        _state.update { it.copy(roomFilesLoading = true, roomFilesPath = path, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.roomWorkspaceListing(roomId, path) } }
                .onSuccess { listing ->
                    _state.update { it.copy(roomFiles = listing, roomFilesPath = listing.path, roomFilesLoading = false) }
                }
                .onFailure { failure ->
                    _state.update { it.copy(roomFilesLoading = false, error = failure.readableMessage(localized)) }
                }
        }
    }

    fun openRoomFolder(path: String) = loadRoomFiles(path)

    /** One level up; the room root has no parent to climb to. */
    fun roomFilesUp() {
        val current = _state.value.roomFilesPath
        if (current.isBlank()) {
            back()
            return
        }
        loadRoomFiles(current.substringBeforeLast('/', ""))
    }

    fun openRoomFile(path: String) {
        val roomId = _state.value.openRoom?.id ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.roomWorkspaceRead(roomId, path) } }
                .onSuccess { content ->
                    _state.update {
                        it.copy(
                            roomFileContent = content,
                            roomFileOpen = it.roomFiles.entries.firstOrNull { entry -> entry.path == path }
                                ?: RoomWorkspaceEntry(path.substringAfterLast('/'), path, false, content.length.toLong(), ""),
                        )
                    }
                }
                .onFailure { failure ->
                    // A binary file lands here: the read route only speaks text.
                    _state.update { it.copy(error = failure.readableMessage(localized)) }
                }
        }
    }

    fun closeRoomFile() = _state.update { it.copy(roomFileOpen = null, roomFileContent = "") }

    fun saveRoomFile(content: String) {
        val roomId = _state.value.openRoom?.id ?: return
        val target = _state.value.roomFileOpen ?: return
        _state.update { it.copy(roomFileSaving = true) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.roomWorkspaceWrite(roomId, target.path, content) } }
                .onSuccess {
                    _state.update {
                        it.copy(
                            roomFileSaving = false,
                            roomFileContent = content,
                            roomFileOpen = null,
                            notice = str(R.string.room_files_saved),
                        )
                    }
                    loadRoomFiles()
                }
                .onFailure { failure ->
                    _state.update { it.copy(roomFileSaving = false, error = failure.readableMessage(localized)) }
                }
        }
    }

    fun createRoomFolder(parent: String, name: String) {
        val roomId = _state.value.openRoom?.id ?: return
        val path = if (parent.isBlank()) name else "$parent/$name"
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.roomWorkspaceMkdir(roomId, path) } }
                .onSuccess { loadRoomFiles() }
                .onFailure { failure -> _state.update { it.copy(error = failure.readableMessage(localized)) } }
        }
    }

    fun renameRoomFile(entry: RoomWorkspaceEntry, newName: String) {
        val roomId = _state.value.openRoom?.id ?: return
        val parent = entry.path.substringBeforeLast('/', "")
        val target = if (parent.isBlank()) newName else "$parent/$newName"
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.roomWorkspaceRename(roomId, entry.path, target) } }
                .onSuccess { loadRoomFiles() }
                .onFailure { failure -> _state.update { it.copy(error = failure.readableMessage(localized)) } }
        }
    }

    fun deleteRoomFile(entry: RoomWorkspaceEntry) {
        val roomId = _state.value.openRoom?.id ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.roomWorkspaceDelete(roomId, entry.path, recursive = entry.isDir) }
            }
                .onSuccess { loadRoomFiles() }
                .onFailure { failure -> _state.update { it.copy(error = failure.readableMessage(localized)) } }
        }
    }

    // ── remote connectors ────────────────────────────────────────────────

    /** Capabilities, connections and pending join requests, in one pass. */
    fun refreshGroupLink() {
        val roomId = _state.value.openRoom?.id ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    Triple(
                        api.groupLinkCapabilities(),
                        api.groupLinkConnections(),
                        api.roomAgentLinkRequests(roomId),
                    )
                }
            }.onSuccess { (capabilities, connections, requests) ->
                _state.update {
                    it.copy(groupLink = capabilities, groupConnections = connections, agentLinkRequests = requests)
                }
            }.onFailure { failure ->
                _state.update { it.copy(error = failure.readableMessage(localized)) }
            }
        }
    }

    fun disconnectGroupLink(connection: GroupLinkConnection) = launchWork(
        work = { api.disconnectGroupLink(connection.connectorId) },
        onSuccess = { refreshGroupLink() },
    )

    /** Approve or deny a remote agent asking to join; the list is re-read either way. */
    fun decideAgentLinkRequest(request: AgentLinkRequest, approved: Boolean) {
        val roomId = _state.value.openRoom?.id ?: return
        launchWork(
            work = { api.decideAgentLinkRequest(roomId, request.id, approved) },
            onSuccess = {
                refreshGroupLink()
                reopenRoom(roomId)
            },
        )
    }

    // ── agent presets ─────────────────────────────────────────────────────

    fun loadAgentPresets() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.agentPresets() } }
                .onSuccess { presets ->
                    _state.update { it.copy(agentPresets = presets, presetsLoaded = true) }
                }
                .onFailure { failure ->
                    _state.update { it.copy(presetsLoaded = true, error = failure.readableMessage(localized)) }
                }
        }
    }

    fun saveAgentPreset(existing: AgentPreset?, draft: RoomAgentDraft) = launchWork(
        work = { if (existing == null) api.createAgentPreset(draft) else api.updateAgentPreset(existing.id, draft) },
        onSuccess = { presets -> _state.update { it.copy(agentPresets = presets) } },
    )

    fun deleteAgentPreset(preset: AgentPreset) = launchWork(
        work = { api.deleteAgentPreset(preset.id) },
        onSuccess = { presets -> _state.update { it.copy(agentPresets = presets) } },
    )

    /** Opens the member/agent/config screen for the room currently on screen. */
    fun openRoomSettings() {
        _state.update { it.copy(screen = Screen.RoomSettings, error = null) }
        loadAgentPresets()
    }

    private fun reopenRoom(roomId: String) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.room(roomId) } }
                .onSuccess { detail -> _state.update { it.copy(openRoom = detail) } }
        }
    }

    /** Sends into the open room over the socket the room screen holds. */
    fun postToRoom(text: String): Boolean {
        val room = _state.value.openRoom ?: return false
        val clean = text.trim()
        if (clean.isBlank()) return false
        val sent = group.post(room.id, clean, _state.value.account ?: "phone") { error ->
            if (error != null) _state.update { it.copy(error = error) }
        }
        if (!sent) {
            _state.update { it.copy(error = str(R.string.error_room_offline)) }
        }
        return sent
    }

    // ── native agent tools ───────────────────────────────────────────────

    fun openKanban() {
        _state.update { it.copy(screen = Screen.Kanban, error = null, notice = null) }
        loadKanban()
    }

    fun selectKanbanBoard(slug: String) {
        _state.update { it.copy(kanban = it.kanban.copy(board = slug, openTask = null)) }
        loadKanban(refreshBoards = false)
    }

    fun refreshKanban() = loadKanban()

    private fun loadKanban(refreshBoards: Boolean = true) {
        val old = _state.value.kanban
        _state.update { it.copy(kanban = it.kanban.copy(loading = true)) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val boards = if (refreshBoards || old.boards.isEmpty()) api.kanbanBoards() else old.boards
                    val selected = old.board.takeIf { slug -> boards.any { it.slug == slug } }
                        ?: boards.firstOrNull { it.isCurrent }?.slug
                        ?: boards.firstOrNull()?.slug.orEmpty()
                    Triple(boards, api.kanbanTasks(selected), api.kanbanAssignees(selected))
                }
            }.onSuccess { (boards, tasks, assignees) ->
                val selected = _state.value.kanban.board.takeIf { slug -> boards.any { it.slug == slug } }
                    ?: boards.firstOrNull { it.isCurrent }?.slug
                    ?: boards.firstOrNull()?.slug.orEmpty()
                _state.update {
                    it.copy(kanban = it.kanban.copy(
                        loading = false,
                        boards = boards,
                        board = selected,
                        tasks = tasks,
                        assignees = assignees,
                    ))
                }
            }.onFailure { failure ->
                _state.update {
                    it.copy(kanban = it.kanban.copy(loading = false), error = failure.readableMessage(localized))
                }
            }
        }
    }

    fun createKanbanTask(
        title: String,
        body: String,
        assignee: String,
        priority: Int,
        skills: List<String>,
        triage: Boolean,
    ) {
        val clean = title.trim()
        if (clean.isBlank()) return
        val board = _state.value.kanban.board
        _state.update { it.copy(kanban = it.kanban.copy(actionId = "new"), error = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.createKanbanTask(board, clean, body.trim(), assignee, priority, skills, triage)
                }
            }.onSuccess { task ->
                _state.update {
                    it.copy(
                        kanban = it.kanban.copy(actionId = null, tasks = it.kanban.tasks + task),
                        notice = str(R.string.kanban_created),
                    )
                }
            }.onFailure { failure ->
                _state.update {
                    it.copy(kanban = it.kanban.copy(actionId = null), error = failure.readableMessage(localized))
                }
            }
        }
    }

    fun moveKanbanTask(task: KanbanTask, status: String) {
        if (task.status == status) return
        val board = _state.value.kanban.board
        _state.update {
            it.copy(
                kanban = it.kanban.copy(
                    actionId = task.id,
                    tasks = it.kanban.tasks.map { current ->
                        if (current.id == task.id) current.copy(status = status) else current
                    },
                ),
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.moveKanbanTask(board, task.id, status) } }
                .onSuccess {
                    _state.update { state -> state.copy(kanban = state.kanban.copy(actionId = null)) }
                    if (_state.value.screen == Screen.KanbanTask) loadKanbanTask(task.id)
                }
                .onFailure { failure ->
                    _state.update { state ->
                        state.copy(
                            kanban = state.kanban.copy(
                                actionId = null,
                                tasks = state.kanban.tasks.map { current ->
                                    if (current.id == task.id) task else current
                                },
                            ),
                            error = failure.readableMessage(localized),
                        )
                    }
                }
        }
    }

    fun openKanbanTask(task: KanbanTask) {
        _state.update {
            it.copy(
                screen = Screen.KanbanTask,
                kanban = it.kanban.copy(openTask = KanbanTaskDetail(task, null, emptyList(), emptyList())),
                error = null,
            )
        }
        loadKanbanTask(task.id)
        loadKanbanOperations(task.id)
    }

    private fun loadKanbanTask(id: String) {
        val board = _state.value.kanban.board
        _state.update { it.copy(kanban = it.kanban.copy(loading = true)) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.kanbanTask(board, id) } }
                .onSuccess { detail ->
                    _state.update {
                        it.copy(
                            kanban = it.kanban.copy(
                                loading = false,
                                openTask = detail,
                                tasks = it.kanban.tasks.map { task ->
                                    if (task.id == detail.task.id) detail.task else task
                                },
                            ),
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(kanban = it.kanban.copy(loading = false), error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun addKanbanComment(taskId: String, body: String) {
        val clean = body.trim()
        if (clean.isBlank()) return
        val board = _state.value.kanban.board
        _state.update { it.copy(kanban = it.kanban.copy(actionId = taskId), error = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.addKanbanComment(board, taskId, clean, _state.value.account) }
            }.onSuccess { loadKanbanTask(taskId) }
                .onFailure { failure ->
                    _state.update {
                        it.copy(kanban = it.kanban.copy(actionId = null), error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun assignKanbanTask(taskId: String, assignee: String) {
        val board = _state.value.kanban.board
        _state.update { it.copy(kanban = it.kanban.copy(actionId = taskId), error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.assignKanbanTask(board, taskId, assignee) } }
                .onSuccess { loadKanbanTask(taskId) }
                .onFailure { failure ->
                    _state.update {
                        it.copy(kanban = it.kanban.copy(actionId = null), error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun openSkills() {
        _state.update { it.copy(screen = Screen.Skills, error = null, notice = null) }
        loadSkills()
    }

    fun selectSkillsTarget(target: String) {
        _state.update { it.copy(skillsUi = it.skillsUi.copy(target = target, openSkill = null)) }
        loadSkills()
    }

    fun refreshSkills() = loadSkills()

    private fun loadSkills() {
        val profile = currentProfile()
        val target = _state.value.skillsUi.target
        _state.update { it.copy(skillsUi = it.skillsUi.copy(loading = true)) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.skills(profile, target) to api.pendingSkillWrites(profile) } }
                .onSuccess { (categories, pendingWrites) ->
                    _state.update { it.copy(skillsUi = it.skillsUi.copy(loading = false, resolvingWriteId = null, categories = categories, pendingWrites = pendingWrites)) }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(skillsUi = it.skillsUi.copy(loading = false, resolvingWriteId = null), error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun resolvePendingSkillWrite(id: String, approve: Boolean) {
        val profile = currentProfile()
        _state.update { it.copy(skillsUi = it.skillsUi.copy(resolvingWriteId = id), error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.resolvePendingSkillWrite(profile, id, approve) } }
                .onSuccess { loadSkills() }
                .onFailure { failure ->
                    _state.update { it.copy(skillsUi = it.skillsUi.copy(resolvingWriteId = null), error = failure.readableMessage(localized)) }
                }
        }
    }

    fun openSkill(category: String, skill: SkillInfo) {
        _state.update { it.copy(screen = Screen.Skill, skillsUi = it.skillsUi.copy(loading = true), error = null) }
        val profile = currentProfile()
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.skillContent(profile, category, skill.name) } }
                .onSuccess { content ->
                    _state.update {
                        it.copy(skillsUi = it.skillsUi.copy(loading = false, openSkill = OpenSkill(category, skill, content)))
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(skillsUi = it.skillsUi.copy(loading = false), error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun saveSkill(content: String) = mutateOpenSkill { profile, open ->
        api.saveSkill(profile, open.category, open.skill.name, content)
    }

    fun toggleSkill(skill: SkillInfo, enabled: Boolean) {
        val profile = currentProfile()
        _state.update { it.copy(skillsUi = it.skillsUi.copy(actionName = skill.name), error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.setSkillEnabled(profile, skill.name, enabled) } }
                .onSuccess {
                    updateSkill(skill.name) { it.copy(enabled = enabled) }
                    _state.update { it.copy(skillsUi = it.skillsUi.copy(actionName = null)) }
                }
                .onFailure { failure -> toolSkillFailure(failure) }
        }
    }

    fun pinSkill(skill: SkillInfo, pinned: Boolean) {
        val profile = currentProfile()
        _state.update { it.copy(skillsUi = it.skillsUi.copy(actionName = skill.name), error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.setSkillPinned(profile, skill.name, pinned) } }
                .onSuccess {
                    updateSkill(skill.name) { it.copy(pinned = pinned) }
                    _state.update { it.copy(skillsUi = it.skillsUi.copy(actionName = null)) }
                }
                .onFailure { failure -> toolSkillFailure(failure) }
        }
    }

    fun deleteOpenSkill() {
        val open = _state.value.skillsUi.openSkill ?: return
        val profile = currentProfile()
        _state.update { it.copy(skillsUi = it.skillsUi.copy(actionName = open.skill.name), error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.deleteSkill(profile, open.category, open.skill.name) } }
                .onSuccess {
                    _state.update { it.copy(screen = Screen.Skills, skillsUi = it.skillsUi.copy(openSkill = null, actionName = null)) }
                    loadSkills()
                }
                .onFailure { failure -> toolSkillFailure(failure) }
        }
    }

    fun importSkill(bytes: ByteArray, filename: String, category: String = "Imported") {
        val profile = currentProfile()
        _state.update { it.copy(skillsUi = it.skillsUi.copy(actionName = "import"), error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.importSkill(profile, category, bytes, filename) } }
                .onSuccess { name ->
                    _state.update {
                        it.copy(skillsUi = it.skillsUi.copy(actionName = null), notice = str(R.string.skills_imported, name))
                    }
                    loadSkills()
                }
                .onFailure { failure -> toolSkillFailure(failure) }
        }
    }

    private fun mutateOpenSkill(block: (String, OpenSkill) -> Unit) {
        val open = _state.value.skillsUi.openSkill ?: return
        val profile = currentProfile()
        _state.update { it.copy(skillsUi = it.skillsUi.copy(actionName = open.skill.name), error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { block(profile, open) } }
                .onSuccess {
                    _state.update {
                        it.copy(
                            skillsUi = it.skillsUi.copy(actionName = null, openSkill = open),
                            notice = str(R.string.skills_saved),
                        )
                    }
                }
                .onFailure { failure -> toolSkillFailure(failure) }
        }
    }

    private fun updateSkill(name: String, transform: (SkillInfo) -> SkillInfo) {
        _state.update { state ->
            val categories = state.skillsUi.categories.map { category ->
                category.copy(skills = category.skills.map { if (it.name == name) transform(it) else it })
            }
            val open = state.skillsUi.openSkill?.let {
                if (it.skill.name == name) it.copy(skill = transform(it.skill)) else it
            }
            state.copy(skillsUi = state.skillsUi.copy(categories = categories, openSkill = open))
        }
    }

    private fun toolSkillFailure(failure: Throwable) {
        _state.update {
            it.copy(skillsUi = it.skillsUi.copy(actionName = null), error = failure.readableMessage(localized))
        }
    }

    fun openPlugins() {
        _state.update { it.copy(screen = Screen.Plugins, error = null, notice = null) }
        loadPlugins()
    }

    fun refreshPlugins() = loadPlugins()

    private fun loadPlugins() {
        _state.update { it.copy(pluginsUi = it.pluginsUi.copy(loading = true)) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.plugins() } }
                .onSuccess { (plugins, warnings) ->
                    _state.update {
                        it.copy(pluginsUi = it.pluginsUi.copy(loading = false, plugins = plugins, warnings = warnings))
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(pluginsUi = it.pluginsUi.copy(loading = false), error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun togglePlugin(plugin: HermesPlugin, enabled: Boolean) {
        _state.update { it.copy(pluginsUi = it.pluginsUi.copy(actionKey = plugin.key), error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.setPluginEnabled(plugin.key, enabled) } }
                .onSuccess { loadPlugins() }
                .onFailure { failure ->
                    _state.update {
                        it.copy(pluginsUi = it.pluginsUi.copy(actionKey = null), error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun openMcp() {
        _state.update { it.copy(screen = Screen.Mcp, error = null, notice = null) }
        loadMcp()
    }

    private fun loadMcp() {
        _state.update { it.copy(mcpUi = it.mcpUi.copy(loading = true)) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.mcpServers() } }
                .onSuccess { servers ->
                    _state.update { it.copy(mcpUi = it.mcpUi.copy(loading = false, actionName = null, servers = servers)) }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(mcpUi = it.mcpUi.copy(loading = false, actionName = null), error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun saveMcpServer(originalName: String?, name: String, config: String) {
        if (name.isBlank()) return
        _state.update { it.copy(mcpUi = it.mcpUi.copy(actionName = originalName ?: "new"), error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.saveMcpServer(originalName, name.trim(), config) } }
                .onSuccess { loadMcp() }
                .onFailure { failure ->
                    _state.update {
                        it.copy(mcpUi = it.mcpUi.copy(actionName = null), error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun deleteMcpServer(name: String) = mutateMcp(name) { api.deleteMcpServer(name) }

    fun testMcpServer(name: String) = mutateMcp(name) { api.testMcpServer(name) }

    fun reloadMcpServer(name: String? = null) = mutateMcp(name ?: "all") { api.reloadMcpServer(name) }

    private fun mutateMcp(name: String, block: () -> Unit) {
        _state.update { it.copy(mcpUi = it.mcpUi.copy(actionName = name), error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess { loadMcp() }
                .onFailure { failure ->
                    _state.update {
                        it.copy(mcpUi = it.mcpUi.copy(actionName = null), error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun openInsights(days: Int = _state.value.usageDays) {
        _state.update { it.copy(screen = Screen.Insights, usageDays = days, loadingInsights = true, error = null) }
        refreshInsights(days)
    }

    /** Switching accounts re-reads the same window for that account only. */
    fun selectUsageAccount(account: String?) {
        _state.update { it.copy(usageAccount = account) }
        refreshInsights(_state.value.usageDays)
    }

    fun refreshInsights(days: Int = _state.value.usageDays) {
        _state.update { it.copy(usageDays = days, loadingInsights = true, error = null) }
        val account = _state.value.usageAccount
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { api.usageStats(days, account) to api.runtimePerformance() }
            }
            result.onSuccess { (usage, performance) ->
                _state.update { it.copy(usageStats = usage, runtimePerformance = performance, loadingInsights = false) }
            }.onFailure { failure ->
                _state.update { it.copy(loadingInsights = false, error = failure.readableMessage(localized)) }
            }
        }
    }

    fun refreshPets() = loadPets()

    private fun loadPets() {
        _state.update { it.copy(petsUi = it.petsUi.copy(loading = true)) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.petdex() to api.activePet() } }
                .onSuccess { (pets, active) ->
                    _state.update { it.copy(petsUi = it.petsUi.copy(loading = false, actionSlug = null, pets = pets, active = active)) }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(petsUi = it.petsUi.copy(loading = false, actionSlug = null), error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun adoptPet(slug: String) {
        _state.update { it.copy(petsUi = it.petsUi.copy(actionSlug = slug), error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.adoptPet(slug) } }
                .onSuccess { active ->
                    _state.update {
                        it.copy(petsUi = it.petsUi.copy(actionSlug = null, active = active), notice = str(R.string.pets_adopted))
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(petsUi = it.petsUi.copy(actionSlug = null), error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun setActivePet(enabled: Boolean? = null, scale: Double? = null) {
        val slug = _state.value.petsUi.active?.slug ?: return
        _state.update { it.copy(petsUi = it.petsUi.copy(actionSlug = slug), error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.updateActivePet(enabled, scale) } }
                .onSuccess { active ->
                    _state.update { it.copy(petsUi = it.petsUi.copy(actionSlug = null, active = active)) }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(petsUi = it.petsUi.copy(actionSlug = null), error = failure.readableMessage(localized))
                    }
                }
        }
    }

    // ── settings ──────────────────────────────────────────────────────────

    // ── settings groups ───────────────────────────────────────────────────

    fun openSettingsGroup(group: SettingsGroup) {
        _state.update {
            it.copy(
                screen = Screen.SettingsGroup,
                openGroup = group,
                toolReturnScreen = when (it.screen) {
                    Screen.AgentHub -> Screen.AgentHub
                    Screen.MoreSettings -> Screen.MoreSettings
                    // The devices account popover opens 关于; back must return there.
                    Screen.Devices -> Screen.Devices
                    else -> Screen.Settings
                },
                error = null,
                notice = null,
                loadingAgentSettings = group == SettingsGroup.Agent,
                loadingStudioSettings = group in STUDIO_CONFIG_GROUPS,
                loadingAccountSettings = group == SettingsGroup.Account,
                loadingManagedUsers = group == SettingsGroup.Users,
                loadingModelProviders = group == SettingsGroup.Models,
            )
        }
        if (group == SettingsGroup.Agent) loadAgentSettings()
        if (group == SettingsGroup.Profile) loadModels()
        if (group in STUDIO_CONFIG_GROUPS) loadStudioSettings()
        if (group == SettingsGroup.Account) loadAccountSettings()
        if (group == SettingsGroup.Users) loadManagedUsers()
        if (group == SettingsGroup.Models) loadModelProviders()
    }

    private fun loadStudioSettings(showLoading: Boolean = true) {
        val profile = currentProfile()
        if (showLoading) _state.update { it.copy(loadingStudioSettings = true) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.studioSettings(profile) } }
                .onSuccess { settings ->
                    _state.update { it.copy(studioSettings = settings, loadingStudioSettings = false) }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(loadingStudioSettings = false, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    /** Saves a partial Studio config section so untouched keys remain unchanged. */
    fun setStudioValue(section: String, key: String, value: Any) {
        saveStudioSection(section, org.json.JSONObject().put(key, value))
    }

    fun saveProxy(https: String, http: String, all: String, noProxy: String) {
        saveStudioSection(
            "proxy",
            org.json.JSONObject()
                .put("HTTPS_PROXY", https.trim())
                .put("HTTP_PROXY", http.trim())
                .put("ALL_PROXY", all.trim())
                .put("NO_PROXY", noProxy.trim()),
        )
    }

    private fun saveStudioSection(section: String, values: org.json.JSONObject) {
        val profile = currentProfile()
        _state.update { it.copy(savingSetting = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    // Studio restarts the gateway for proxy and protected-action
                    // approval changes so those values take effect immediately.
                    api.updateConfigSection(
                        profile,
                        section,
                        values,
                        restart = section == "proxy" || section == "approvals",
                    )
                }
            }.onSuccess {
                _state.update { it.copy(savingSetting = false, notice = str(R.string.notice_saved)) }
                loadStudioSettings(showLoading = false)
            }.onFailure { failure ->
                _state.update { it.copy(savingSetting = false, error = failure.readableMessage(localized)) }
            }
        }
    }

    private fun loadAccountSettings() {
        _state.update { it.copy(loadingAccountSettings = true) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val user = api.currentUser()
                    AccountSettingsData(user, api.lockedIps(), api.myAvatar(user.username))
                }
            }.onSuccess { (user, locks, avatar) ->
                _state.update {
                    it.copy(
                        account = user.username,
                        currentUser = user,
                        lockedIps = locks,
                        accountAvatar = avatar,
                        loadingAccountSettings = false,
                    )
                }
            }.onFailure { failure ->
                _state.update {
                    it.copy(loadingAccountSettings = false, error = failure.readableMessage(localized))
                }
            }
        }
    }

    fun changeAccountPassword(currentPassword: String, newPassword: String) {
        accountWork { api.changePassword(currentPassword, newPassword) }
    }

    fun changeAccountUsername(currentPassword: String, newUsername: String) {
        accountWork {
            api.changeUsername(currentPassword, newUsername)
            loadAccountSettings()
        }
    }

    fun setAccountAvatar(bytes: ByteArray, mime: String) {
        if (mime !in setOf("image/png", "image/jpeg", "image/webp")) {
            _state.update { it.copy(error = str(R.string.account_avatar_invalid_type)) }
            return
        }
        if (bytes.size > 1024 * 1024) {
            _state.update { it.copy(error = str(R.string.account_avatar_too_large)) }
            return
        }
        accountWork {
            val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            api.updateMyAvatar("data:$mime;base64,$encoded")
            loadAccountSettings()
        }
    }

    fun randomizeAccountAvatar() = accountWork {
        val seed = "${_state.value.account.orEmpty()}-${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}"
        val svg = MultiAvatar.svg(getApplication(), seed)
        val encoded = android.util.Base64.encodeToString(svg.toByteArray(), android.util.Base64.NO_WRAP)
        api.updateMyAvatar("data:image/svg+xml;base64,$encoded", seed)
        loadAccountSettings()
    }

    fun resetAccountAvatar() = accountWork {
        api.resetMyAvatar()
        loadAccountSettings()
    }

    fun unlockIp(ip: String) = accountWork {
        api.unlockIp(ip)
        loadAccountSettings()
    }

    fun unlockAllIps() = accountWork {
        api.unlockAllIps()
        loadAccountSettings()
    }

    private fun accountWork(block: suspend () -> Unit) {
        _state.update { it.copy(savingSetting = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess { _state.update { it.copy(savingSetting = false, notice = str(R.string.notice_saved)) } }
                .onFailure { failure ->
                    _state.update { it.copy(savingSetting = false, error = failure.readableMessage(localized)) }
                }
        }
    }

    private fun loadManagedUsers() {
        _state.update { it.copy(loadingManagedUsers = true) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.managedUsers() } }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            managedUsers = result.users,
                            managedProfiles = result.profiles,
                            loadingManagedUsers = false,
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(loadingManagedUsers = false, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun saveManagedUser(existingId: Int?, draft: ManagedUserDraft) {
        _state.update { it.copy(savingSetting = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (existingId == null) api.createManagedUser(draft)
                    else api.updateManagedUser(existingId, draft)
                }
            }.onSuccess {
                _state.update { it.copy(savingSetting = false, notice = str(R.string.notice_saved)) }
                loadManagedUsers()
            }.onFailure { failure ->
                _state.update { it.copy(savingSetting = false, error = failure.readableMessage(localized)) }
            }
        }
    }

    fun deleteManagedUser(user: ManagedUser) {
        _state.update { it.copy(savingSetting = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.deleteManagedUser(user.id) } }
                .onSuccess {
                    _state.update { it.copy(savingSetting = false, notice = str(R.string.notice_saved)) }
                    loadManagedUsers()
                }
                .onFailure { failure ->
                    _state.update { it.copy(savingSetting = false, error = failure.readableMessage(localized)) }
                }
        }
    }

    private fun loadModelProviders() {
        val profile = currentProfile()
        _state.update { it.copy(loadingModelProviders = true) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.modelProviders(profile) } }
                .onSuccess { providers ->
                    _state.update { it.copy(modelProviders = providers, loadingModelProviders = false) }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(loadingModelProviders = false, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun saveProviderKey(provider: String, key: String) {
        val profile = currentProfile()
        _state.update { it.copy(savingSetting = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.updateProviderApiKey(profile, provider, key.trim()) }
            }.onSuccess {
                _state.update { it.copy(savingSetting = false, notice = str(R.string.notice_saved)) }
                loadModelProviders()
            }.onFailure { failure ->
                _state.update { it.copy(savingSetting = false, error = failure.readableMessage(localized)) }
            }
        }
    }

    private fun loadAgentSettings() {
        val profile = _state.value.activeProfile.ifBlank { "default" }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.agentSettings(profile) to api.autoStartPolicy() }
            }.onSuccess { (agent, policy) ->
                _state.update {
                    it.copy(agentSettings = agent, autoStart = policy, loadingAgentSettings = false)
                }
            }.onFailure { failure ->
                _state.update {
                    it.copy(loadingAgentSettings = false, error = failure.readableMessage(localized))
                }
            }
        }
    }

    /** Writes one agent knob; the server keeps the rest of the section as it is. */
    fun setAgentValue(key: String, value: Any) {
        val profile = _state.value.activeProfile.ifBlank { "default" }
        _state.update { it.copy(savingSetting = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.updateConfigSection(profile, "agent", org.json.JSONObject().put(key, value))
                }
            }.onSuccess {
                _state.update { it.copy(savingSetting = false, notice = str(R.string.notice_saved)) }
                loadAgentSettings()
            }.onFailure { failure ->
                _state.update { it.copy(savingSetting = false, error = failure.readableMessage(localized)) }
            }
        }
    }

    fun setAutoStart(policy: AutoStartPolicy) {
        val previous = _state.value.autoStart
        _state.update { it.copy(autoStart = policy, savingSetting = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.setAutoStartPolicy(policy) } }
                .onSuccess {
                    _state.update { it.copy(savingSetting = false, notice = str(R.string.notice_saved)) }
                    loadAgentSettings()
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(savingSetting = false, autoStart = previous, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    // ── scheduled jobs ──────────────────────────────────────────────────

    fun openCronJobs() {
        _state.update {
            it.copy(
                screen = Screen.CronJobs,
                toolReturnScreen = when (it.screen) {
                    Screen.AgentHub -> Screen.AgentHub
                    Screen.MoreSettings -> Screen.MoreSettings
                    else -> Screen.Settings
                },
                error = null,
                notice = null,
                editingCronJob = null,
                cronEditorJobId = null,
                openCronRun = null,
            )
        }
        refreshCronJobs()
    }

    fun refreshCronJobs() {
        if (_state.value.cronLoading) return
        val profile = _state.value.activeProfile.ifBlank { "default" }
        _state.update { it.copy(cronLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.cronJobs(profile) } }
                .onSuccess { jobs ->
                    _state.update {
                        it.copy(cronJobs = jobs.sortedBy { job -> job.name.lowercase() }, cronLoading = false)
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(cronLoading = false, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    /** Opens a blank editor, or reloads one raw job before editing it. */
    fun openCronJob(jobId: String? = null) {
        val profile = _state.value.activeProfile.ifBlank { "default" }
        _state.update {
            it.copy(
                screen = Screen.CronJob,
                editingCronJob = null,
                cronEditorJobId = jobId,
                cronEditorLoading = true,
                error = null,
                notice = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    CronEditorData(
                        job = jobId?.let { api.cronJob(profile, it) },
                        // These enrich the form, but a missing optional endpoint
                        // must not make the core job impossible to edit.
                        models = runCatching { api.availableModels(profile) }.getOrDefault(emptyList()),
                        skills = runCatching { api.cronSkills(profile) }.getOrDefault(emptyList()),
                        targets = runCatching { api.cronDeliveryTargets(profile) }.getOrDefault(emptyList()),
                    )
                }
            }.onSuccess { data ->
                _state.update {
                    it.copy(
                        editingCronJob = data.job,
                        models = data.models,
                        modelsProfile = profile,
                        cronSkills = data.skills,
                        cronDeliveryTargets = data.targets,
                        cronEditorLoading = false,
                    )
                }
            }.onFailure { failure ->
                _state.update {
                    it.copy(cronEditorLoading = false, error = failure.readableMessage(localized))
                }
            }
        }
    }

    fun saveCronJob(draft: CronJobDraft) {
        if (_state.value.savingSetting) return
        if (draft.name.isBlank()) {
            _state.update { it.copy(error = str(R.string.cron_name_required)) }
            return
        }
        if (draft.schedule.isBlank()) {
            _state.update { it.copy(error = str(R.string.cron_schedule_required)) }
            return
        }
        if (draft.prompt.isBlank()) {
            _state.update { it.copy(error = str(R.string.cron_prompt_required)) }
            return
        }
        if (draft.repeatTimes != null && draft.repeatTimes < 1) {
            _state.update { it.copy(error = str(R.string.cron_repeat_invalid)) }
            return
        }

        val profile = _state.value.activeProfile.ifBlank { "default" }
        val original = _state.value.editingCronJob
        _state.update { it.copy(savingSetting = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (original == null) api.createCronJob(profile, draft)
                    else api.updateCronJob(profile, original, draft)
                }
            }.onSuccess { saved ->
                _state.update {
                    it.copy(
                        screen = Screen.CronJobs,
                        savingSetting = false,
                        editingCronJob = null,
                        cronEditorJobId = null,
                        cronJobs = it.cronJobs.upsert(saved),
                        notice = str(
                            if (original == null) R.string.cron_notice_created else R.string.cron_notice_updated,
                        ),
                    )
                }
            }.onFailure { failure ->
                _state.update {
                    it.copy(savingSetting = false, error = failure.readableMessage(localized))
                }
            }
        }
    }

    fun toggleCronJob(job: CronJob) {
        if (_state.value.cronActionId != null) return
        val profile = _state.value.activeProfile.ifBlank { "default" }
        val resume = job.state == "paused" || !job.enabled
        _state.update { it.copy(cronActionId = job.id, error = null, notice = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (resume) api.resumeCronJob(profile, job.id) else api.pauseCronJob(profile, job.id)
                }
            }.onSuccess { updated ->
                _state.update {
                    it.copy(
                        cronActionId = null,
                        cronJobs = it.cronJobs.upsert(updated),
                        notice = str(if (resume) R.string.cron_notice_resumed else R.string.cron_notice_paused),
                    )
                }
            }.onFailure { failure ->
                _state.update {
                    it.copy(cronActionId = null, error = failure.readableMessage(localized))
                }
            }
        }
    }

    fun runCronJob(job: CronJob) {
        if (_state.value.cronActionId != null) return
        val profile = _state.value.activeProfile.ifBlank { "default" }
        _state.update { it.copy(cronActionId = job.id, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.runCronJob(profile, job.id) } }
                .onSuccess { updated ->
                    _state.update {
                        it.copy(
                            cronActionId = null,
                            cronJobs = it.cronJobs.upsert(updated),
                            notice = str(R.string.cron_notice_triggered),
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(cronActionId = null, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun deleteCronJob(job: CronJob) {
        if (_state.value.cronActionId != null) return
        val profile = _state.value.activeProfile.ifBlank { "default" }
        _state.update { it.copy(cronActionId = job.id, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.deleteCronJob(profile, job.id) } }
                .onSuccess {
                    _state.update {
                        it.copy(
                            cronActionId = null,
                            cronJobs = it.cronJobs.filterNot { candidate -> candidate.id == job.id },
                            notice = str(R.string.cron_notice_deleted),
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(cronActionId = null, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun openCronHistory(job: CronJob) {
        _state.update {
            it.copy(
                screen = Screen.CronHistory,
                cronHistoryJob = job,
                cronRuns = emptyList(),
                openCronRun = null,
                error = null,
                notice = null,
            )
        }
        refreshCronHistory()
    }

    fun refreshCronHistory() {
        val job = _state.value.cronHistoryJob ?: return
        if (_state.value.cronHistoryLoading) return
        val profile = _state.value.activeProfile.ifBlank { "default" }
        _state.update { it.copy(cronHistoryLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.cronRuns(profile, job.id) } }
                .onSuccess { runs ->
                    _state.update { it.copy(cronRuns = runs, cronHistoryLoading = false) }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(cronHistoryLoading = false, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun openCronRun(run: CronRun) {
        if (_state.value.cronRunLoading) return
        val profile = _state.value.activeProfile.ifBlank { "default" }
        _state.update { it.copy(cronRunLoading = true, openCronRun = null, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.cronRun(profile, run) } }
                .onSuccess { detail ->
                    _state.update { it.copy(cronRunLoading = false, openCronRun = detail) }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(cronRunLoading = false, error = failure.readableMessage(localized))
                    }
                }
        }
    }

    fun dismissCronRun() = _state.update { it.copy(openCronRun = null) }

    // ── channels ──────────────────────────────────────────────────────────

    fun openChannels() {
        _state.update {
            it.copy(
                screen = Screen.Channels,
                toolReturnScreen = when (it.screen) {
                    Screen.AgentHub -> Screen.AgentHub
                    Screen.MoreSettings -> Screen.MoreSettings
                    else -> Screen.Settings
                },
                error = null,
                notice = null,
            )
        }
        refreshServerConfig()
    }

    /** Settings itself only needs the channel counts and the default model. */

    // ── official pages/devices ────────────────────────────────────────────

    /**
     * Opens the devices screen and starts the reachability pass.
     *
     * The official page does the same on mount: render the locally known devices
     * immediately, mark every one of them 检测中, then settle each card on
     * 在线/离线 as its probe returns.
     */
    fun openDevices() {
        _state.update { it.copy(screen = Screen.Devices, error = null, notice = null) }
        probeDevices()
    }

    /** The official `.retry-action` ("重新同步") on the in-grid error state. */
    fun refreshDevices() = probeDevices()

    /**
     * The official `pe(devices)` / `ge(device)` pass: every saved Studio is asked
     * who it is, using its own URL and its own token, so the list can show real
     * presence instead of "the one we happen to be connected to".
     *
     * Devices are probed concurrently and the results are committed as they
     * arrive — a slow or unreachable Studio must not hold up the others.
     */
    private fun probeDevices() {
        val targets = store.instances
        if (targets.isEmpty()) {
            _state.update { it.copy(devicesUi = it.devicesUi.copy(checking = emptySet())) }
            return
        }
        probeJob?.cancel()
        _state.update {
            it.copy(devicesUi = it.devicesUi.copy(checking = targets.map { t -> t.url }.toSet()))
        }
        probeJob = viewModelScope.launch {
            targets.forEach { item ->
                launch {
                    val probe = withContext(Dispatchers.IO) {
                        runCatching {
                            // A throwaway client: probing must never move the
                            // shared api/chat clients off the active Studio.
                            val client = HermesApi(item.url, item.token)
                            val username = client.verifyToken()
                            val versions = runCatching { client.runtimeVersions() }.getOrNull()
                            DeviceProbe(
                                online = true,
                                username = username,
                                webUiVersion = versions?.activeWebUi.orEmpty(),
                                platform = versions?.platform.orEmpty(),
                                checkedAt = System.currentTimeMillis() / 1000,
                            )
                        }.getOrElse { DeviceProbe(online = false, checkedAt = System.currentTimeMillis() / 1000) }
                    }
                    _state.update { state ->
                        state.copy(
                            devicesUi = state.devicesUi.copy(
                                checking = state.devicesUi.checking - item.url,
                                probes = state.devicesUi.probes + (item.url to probe),
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * Official `he(device)`: tapping a card opens that Studio.
     *
     * [switchInstance] does the actual pointing; this wrapper only adds the
     * 连接中 card state and turns a failure into a card-level error rather than
     * dropping the user on an empty Loading screen.
     */
    fun connectDevice(url: String) {
        val target = store.instances.firstOrNull { it.url == url.trimEnd('/') } ?: return
        if (target.url == store.baseUrl && store.token.isNotBlank()) {
            _state.update { it.copy(screen = Screen.Chats, error = null) }
            return
        }
        _state.update { it.copy(devicesUi = it.devicesUi.copy(connecting = target.url), error = null) }
        viewModelScope.launch {
            val probe = withContext(Dispatchers.IO) {
                runCatching {
                    val client = HermesApi(target.url, target.token)
                    val username = client.verifyToken()
                    val versions = runCatching { client.runtimeVersions() }.getOrNull()
                    DeviceProbe(
                        online = true,
                        username = username,
                        webUiVersion = versions?.activeWebUi.orEmpty(),
                        platform = versions?.platform.orEmpty(),
                        checkedAt = System.currentTimeMillis() / 1000,
                    )
                }.getOrElse { DeviceProbe(online = false, checkedAt = System.currentTimeMillis() / 1000) }
            }
            _state.update {
                it.copy(devicesUi = it.devicesUi.copy(connecting = null, probes = it.devicesUi.probes + (target.url to probe)))
            }
            if (probe.online) {
                // What the probe learned is worth keeping: the card renders
                // "Studio <version>" and the OS family from here on, the way the
                // official app caches device metadata on connect.
                rememberDeviceDetails(
                    target.url,
                    probe,
                    if (target.username.isBlank()) probe.username else target.username,
                )
                switchInstance(target.url)
            } else {
                _state.update { it.copy(error = str(R.string.devices_unreachable, target.host)) }
            }
        }
    }

    /**
     * Points every client at another saved deployment and reloads its sessions.
     *
     * The official `he(device)` lands on `pages/index`; this app's equivalent
     * entry point is the chats list of the newly active Studio.
     */
    fun switchInstance(url: String) {
        val target = store.instances.firstOrNull { it.url == url.trimEnd('/') } ?: return
        if (target.url == store.baseUrl) {
            _state.update { it.copy(screen = Screen.Chats) }
            return
        }
        val touched = upsertInstance(store.instances, target.copy(lastUsedAt = System.currentTimeMillis()))
        store.instances = touched
        store.baseUrl = target.url
        store.token = target.token
        api.update(target.url, target.token)
        chat.update(target.url, target.token)
        group.update(target.url, target.token)
        _state.update {
            it.copy(
                baseUrl = target.url,
                instances = touched,
                screen = Screen.Loading,
                openSession = null,
                lines = emptyList(),
                sessions = emptyList(),
                rooms = emptyList(),
                account = target.username,
                error = null,
                notice = null,
            )
        }
        restoreSession()
    }

    private fun rememberDeviceDetails(url: String, probe: DeviceProbe, username: String) {
        val updated = store.instances.map { item ->
            if (item.url != url) {
                item
            } else {
                item.copy(
                    username = username,
                    platform = probe.platform.ifBlank { item.platform },
                    webUiVersion = probe.webUiVersion.ifBlank { item.webUiVersion },
                    endpointKind = item.endpointKind.ifBlank { DEFAULT_ENDPOINT_KIND },
                    lastSeenAt = if (probe.online) probe.checkedAt else item.lastSeenAt,
                )
            }
        }
        store.instances = updated
        _state.update { it.copy(instances = updated) }
    }

    /** The login form doubles as "add another instance". */
    /**
     * Official `.pairing-submit` in the manual-add panel: 设备连接 + 设备名称 +
     * Studio 账号 + Studio 密码 → "登录并添加设备".
     *
     * This is the same sign-in the login screen runs, so it goes through
     * [login] rather than duplicating the token exchange; the only additions are
     * the device display name and the connection normaliser, which is a port of
     * the official one (see [normalizeDeviceConnection]).
     */
    fun addDevice(connection: String, name: String, username: String, password: String) {
        val url = normalizeDeviceConnection(connection)
        if (url == null) {
            _state.update { it.copy(error = str(R.string.devices_error_connection)) }
            return
        }
        if (username.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = str(R.string.error_credentials_required)) }
            return
        }
        val label = name.trim().take(40).ifBlank { hostLabel(url) }
        _state.update { it.copy(devicesUi = it.devicesUi.copy(saving = true), error = null) }
        login(url, username, password, remember = false, label = label)
    }

    fun renameInstance(url: String, label: String) {
        val updated = store.instances.map { item ->
            if (item.url == url) item.copy(label = label.trim().take(40).ifBlank { item.host }) else item
        }
        store.instances = updated
        _state.update { it.copy(instances = updated, devicesUi = it.devicesUi.copy(saving = false)) }
    }

    /**
     * Official `Se(device)` — the card's danger action. It removes the saved
     * connection on this phone only; the Studio itself is untouched.
     */
    fun removeInstance(url: String) {
        // Official `Ee(device)` marks the card 删除中 while its removal is in
        // flight. The write here is local, so the flag is set for the same
        // reason the official one is — the branch is real, it is just rarely
        // observable (see docs/parity/devices.md).
        _state.update { it.copy(devicesUi = it.devicesUi.copy(deleting = url)) }
        val remaining = store.instances.filterNot { it.url == url }
        store.instances = remaining
        val wasActive = store.baseUrl == url
        if (wasActive) {
            val next = remaining.firstOrNull()
            store.baseUrl = next?.url.orEmpty()
            store.token = next?.token.orEmpty()
            api.update(store.baseUrl, store.token)
            chat.update(store.baseUrl, store.token)
            group.update(store.baseUrl, store.token)
        }
        _state.update {
            it.copy(
                instances = remaining,
                devicesUi = it.devicesUi.copy(deleting = null, probes = it.devicesUi.probes - url),
                error = null,
            )
        }
    }

    /**
     * The official `ApiRouteSwitch` picker. [ApiRoute.CLOUD] is rejected here
     * rather than merely greyed out in the UI, so no future call site can route
     * traffic at the vendor's cloud by accident.
     */
    fun setApiRoute(route: String) {
        if (route != ApiRoute.LAN) {
            _state.update { it.copy(error = str(R.string.devices_route_cloud_unavailable)) }
            return
        }
        store.apiRoute = route
        _state.update { it.copy(devicesUi = it.devicesUi.copy(route = route), error = null) }
    }

    /** The account popover's 关于 action, reachable from the devices screen. */
    fun openAboutFromDevices() {
        _state.update { it.copy(toolReturnScreen = Screen.Devices) }
        openSettingsGroup(SettingsGroup.About)
    }

    fun openSettings() {
        _state.update { it.copy(screen = Screen.Settings, error = null, notice = null, openGroup = null) }
        refreshServerConfig()
    }

    fun openProfiles() {
        _state.update { state ->
            val parent = when (state.screen) {
                Screen.Chats, Screen.Groups, Screen.AgentHub,
                Screen.Settings, Screen.SettingsGroup, Screen.MoreSettings,
                -> state.screen
                else -> when (state.tab) {
                    Tab.Groups -> Screen.Groups
                    Tab.Agent -> Screen.AgentHub
                    Tab.Chats -> Screen.Chats
                }
            }
            state.copy(
                screen = Screen.Profiles,
                profilesReturnScreen = parent,
                error = null,
                notice = null,
            )
        }
    }

    fun openMoreSettings() {
        _state.update { it.copy(screen = Screen.MoreSettings, error = null, notice = null, openGroup = null) }
    }

    fun openChannel(platform: String) {
        _state.update {
            it.copy(
                screen = Screen.Channel,
                openChannel = platform,
                weixinQr = if (platform == "weixin") it.weixinQr else WeixinQrUi(),
                error = null,
                notice = null,
            )
        }
    }

    /** Completes Weixin's Studio QR flow without leaving the native editor. */
    fun startWeixinQr() {
        val profile = _state.value.activeProfile.ifBlank { "default" }
        weixinQrJob?.cancel()
        _state.update { it.copy(weixinQr = WeixinQrUi(status = "loading"), error = null, notice = null) }
        weixinQrJob = viewModelScope.launch {
            val code = runCatching { withContext(Dispatchers.IO) { api.weixinQrCode(profile) } }
                .getOrElse { failure ->
                    _state.update {
                        it.copy(weixinQr = WeixinQrUi(status = "error"), error = failure.readableMessage(localized))
                    }
                    return@launch
                }
            _state.update {
                it.copy(weixinQr = WeixinQrUi(status = "waiting", id = code.id, url = code.url))
            }

            repeat(100) {
                delay(3_000)
                val poll = runCatching {
                    withContext(Dispatchers.IO) { api.weixinQrStatus(profile, code.id) }
                }.getOrNull() ?: return@repeat
                when (poll.status) {
                    "confirmed" -> {
                        runCatching {
                            withContext(Dispatchers.IO) { api.saveWeixinCredentials(profile, poll) }
                        }.onSuccess {
                            _state.update {
                                it.copy(
                                    weixinQr = WeixinQrUi(status = "confirmed", id = code.id),
                                    notice = str(R.string.notice_weixin_linked),
                                )
                            }
                            refreshServerConfig()
                        }.onFailure { failure ->
                            _state.update {
                                it.copy(
                                    weixinQr = WeixinQrUi(status = "error", id = code.id),
                                    error = failure.readableMessage(localized),
                                )
                            }
                        }
                        return@launch
                    }
                    "expired" -> {
                        _state.update { it.copy(weixinQr = WeixinQrUi(status = "expired", id = code.id)) }
                        return@launch
                    }
                    "scaned", "scaned_but_redirect" -> _state.update {
                        it.copy(weixinQr = it.weixinQr.copy(status = "scanned"))
                    }
                    else -> Unit
                }
            }
            _state.update { it.copy(weixinQr = WeixinQrUi(status = "expired", id = code.id)) }
        }
    }

    private fun refreshServerConfig() {
        val profile = _state.value.activeProfile.ifBlank { "default" }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.serverConfig(profile) } }
                .onSuccess { config ->
                    _state.update { it.copy(serverConfig = config, defaultModel = config.defaultModel) }
                }
                .onFailure { failure -> _state.update { it.copy(error = failure.readableMessage(localized)) } }
        }
    }

    /**
     * Writes a channel's credentials. The server restarts the gateway itself
     * once they land, which is what actually puts the channel online.
     */
    fun saveChannel(platform: String, values: Map<String, String>, enabled: Boolean) {
        val profile = _state.value.activeProfile.ifBlank { "default" }
        val spec = channelSpec(platform)
        val label = spec.label
        _state.update { it.copy(savingSetting = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val credentials = linkedMapOf<String, Any?>()
                    val configuration = org.json.JSONObject()

                    fun putNested(target: org.json.JSONObject, path: String, value: Any?) {
                        val parts = path.split('.')
                        var current = target
                        parts.dropLast(1).forEach { part ->
                            current = current.optJSONObject(part)
                                ?: org.json.JSONObject().also { current.put(part, it) }
                        }
                        current.put(parts.last(), value ?: org.json.JSONObject.NULL)
                    }

                    spec.fields.forEach { field ->
                        val raw = values[field.path].orEmpty()
                        val value: Any = when (field.kind) {
                            ChannelFieldKind.Toggle -> raw.toBooleanStrictOrNull() ?: field.defaultEnabled
                            ChannelFieldKind.CommaList -> org.json.JSONArray().apply {
                                raw.split(',').map(String::trim).filter(String::isNotEmpty).forEach(::put)
                            }
                            ChannelFieldKind.Text, ChannelFieldKind.Secret -> raw.trim()
                        }
                        if (field.target == ChannelFieldTarget.Credentials) credentials[field.path] = value
                        else putNested(configuration, field.path, value)
                    }

                    // WhatsApp owns its enablement through WHATSAPP_ENABLED.
                    // Other adapters retain the convenient app-level switch.
                    if (spec.fields.none {
                            it.target == ChannelFieldTarget.Credentials && it.path == "enabled"
                        }
                    ) {
                        configuration.put("enabled", enabled)
                    }

                    if (configuration.length() > 0) {
                        api.updateConfigSection(
                            profile,
                            platform,
                            configuration,
                            restart = credentials.isEmpty(),
                        )
                    }
                    if (credentials.isNotEmpty()) {
                        // Empty strings are intentional: Studio uses them to clear
                        // one incorrect value without deleting every credential.
                        api.updateChannelCredentials(profile, platform, credentials)
                    }
                }
            }.onSuccess {
                _state.update {
                    it.copy(savingSetting = false, notice = str(R.string.notice_channel_saved, label))
                }
                refreshServerConfig()
            }.onFailure { failure ->
                _state.update { it.copy(savingSetting = false, error = failure.readableMessage(localized)) }
            }
        }
    }

    fun clearChannel(platform: String) {
        val profile = _state.value.activeProfile.ifBlank { "default" }
        val label = channelSpec(platform).label
        _state.update { it.copy(savingSetting = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.clearChannelCredentials(profile, platform) } }
                .onSuccess {
                    _state.update {
                        it.copy(savingSetting = false, notice = str(R.string.notice_channel_cleared, label))
                    }
                    refreshServerConfig()
                }
                .onFailure { failure ->
                    _state.update { it.copy(savingSetting = false, error = failure.readableMessage(localized)) }
                }
        }
    }

    /** Whether the gateway comes up with the server, written server-side. */
    /** Writes the profile default, which is what new conversations start from. */
    fun setDefaultModel(option: ModelOption) {
        val profile = _state.value.activeProfile.ifBlank { "default" }
        _state.update { it.copy(savingSetting = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.setDefaultModel(profile, option.id, option.provider) }
            }.onSuccess {
                _state.update {
                    it.copy(
                        savingSetting = false,
                        defaultModel = option.id,
                        notice = str(R.string.notice_default_model, profile, option.id),
                    )
                }
            }.onFailure { failure ->
                _state.update { it.copy(savingSetting = false, error = failure.readableMessage(localized)) }
            }
        }
    }

    fun restartGateway() {
        val profile = _state.value.activeProfile.ifBlank { "default" }
        _state.update { it.copy(savingSetting = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.restartGateway(profile) } }
                .onSuccess {
                    _state.update {
                        it.copy(savingSetting = false, notice = str(R.string.notice_gateway_restarting, profile))
                    }
                }
                .onFailure { failure ->
                    _state.update { it.copy(savingSetting = false, error = failure.readableMessage(localized)) }
                }
        }
    }

    /** Replaces the app mark with a picture from the device. */
    fun setAppLogo(bytes: ByteArray) {
        viewModelScope.launch {
            val applied = AppLogo.setCustom(getApplication<Application>(), bytes)
            _state.update {
                if (applied) it.copy(notice = str(R.string.notice_logo_updated), error = null)
                else it.copy(error = str(R.string.error_image_unreadable))
            }
        }
    }

    /** Goes back to whatever logo the connected Studio serves. */
    fun resetAppLogo() {
        viewModelScope.launch {
            AppLogo.clearCustom(getApplication<Application>())
            runCatching { AppLogo.syncFromServer(getApplication<Application>(), api, force = true) }
            _state.update {
                if (AppLogo.image != null) {
                    it.copy(notice = str(R.string.notice_logo_from_server), error = null)
                } else {
                    it.copy(error = str(R.string.error_no_server_logo))
                }
            }
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    // ── misc ──────────────────────────────────────────────────────────────

    fun back() {
        when (_state.value.screen) {
            // Leaving a conversation only detaches this phone. The explicit
            // stop button is the action that aborts an agent run on Studio.
            Screen.Conversation -> cancelActiveRun(abort = false)
            Screen.Room -> leaveRoom()
            // Room settings are part of the room; back goes to the conversation.
            Screen.RoomSettings -> _state.update { it.copy(screen = Screen.Room) }
            Screen.RoomFiles -> _state.update { it.copy(screen = Screen.RoomSettings) }
            Screen.WorkflowRun -> _state.update { it.copy(screen = Screen.AgentHub, openWorkflowRun = null, openWorkflowRunId = null) }
            else -> Unit
        }
        _state.update { state ->
            val visitedTarget = generateSequence { navigationHistory.removeLastOrNull() }
                .firstOrNull { it != state.screen && !it.isTransientDestination() }
            val target = visitedTarget ?: when (state.screen) {
                Screen.Channel -> Screen.Channels
                Screen.CronJob, Screen.CronHistory -> Screen.CronJobs
                Screen.KanbanTask -> Screen.Kanban
                Screen.Skill -> Screen.Skills
                Screen.Kanban, Screen.Skills, Screen.Plugins, Screen.Mcp, Screen.Pets, Screen.Insights, Screen.AgentRuntimes, Screen.Workflows, Screen.GlobalAgent, Screen.EkkoHub, Screen.Files, Screen.Logs, Screen.Connections, Screen.Journey, Screen.Webhooks, Screen.RuntimeVersions, Screen.Appearance, Screen.Pricing, Screen.ApiKeys -> Screen.AgentHub
                Screen.Channels, Screen.SettingsGroup, Screen.CronJobs -> state.toolReturnScreen
                Screen.Profiles -> state.profilesReturnScreen
                Screen.Devices -> Screen.Chats
                Screen.MoreSettings -> Screen.Settings
                else -> when (state.tab) {
                    Tab.Groups -> Screen.Groups
                    Tab.Agent -> Screen.AgentHub
                    Tab.Chats -> Screen.Chats
                }
            }
            consumingBackNavigation = true
            state.copy(
                screen = target,
                error = null,
                taskPlans = if (state.screen == Screen.Conversation) emptyList() else state.taskPlans,
                openSession = state.openSession.takeUnless { state.screen == Screen.Conversation },
                openRoom = state.openRoom.takeUnless { state.screen == Screen.Room },
                attachments = if (state.screen == Screen.Conversation) emptyList() else state.attachments,
                sessionModel = state.sessionModel.takeUnless { state.screen == Screen.Conversation },
                sessionProvider = state.sessionProvider.takeUnless { state.screen == Screen.Conversation },
                editingCronJob = state.editingCronJob.takeUnless { state.screen == Screen.CronJob },
                cronEditorJobId = state.cronEditorJobId.takeUnless { state.screen == Screen.CronJob },
                openCronRun = null,
                kanban = state.kanban.copy(
                    openTask = state.kanban.openTask.takeUnless { state.screen == Screen.KanbanTask },
                ),
                skillsUi = state.skillsUi.copy(
                    openSkill = state.skillsUi.openSkill.takeUnless { state.screen == Screen.Skill },
                ),
            )
        }
    }

    fun show(screen: Screen) = _state.update { it.copy(screen = screen, error = null) }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** Lets device pickers surface a readable error without leaking UI concerns into them. */
    fun showToolError(failure: Throwable) = _state.update {
        it.copy(error = failure.readableMessage(localized))
    }

    fun signOut() {
        cancelActiveRun(abort = true)
        leaveRoom()
        store.clearCredentials()
        _state.update {
            UiState(
                screen = Screen.Login,
                baseUrl = store.baseUrl,
                reasoningEffort = store.reasoningEffort,
                language = store.language,
                appearance = store.appearance,
                instances = store.instances,
                rememberCredentials = store.rememberCredentials,
                savedUsername = store.savedUsername,
                savedPassword = store.savedPassword,
            )
        }
    }

    /** Chooses the language for every screen; the activity restarts to apply it. */
    fun setLanguage(tag: String) {
        store.language = tag
        _state.update { it.copy(language = tag) }
    }

    fun setAppearance(value: String) {
        store.appearance = value
        _state.update { it.copy(appearance = value) }
    }

    /** Global text scale; 1f follows the system font size. */
    fun setTextScale(value: Float) {
        store.textScale = value
        _state.update { it.copy(textScale = store.textScale) }
    }

    /** Asks the activity to re-check GitHub for a newer build. */
    fun requestAppUpdateCheck() {
        _state.update { it.copy(appUpdateCheckTick = it.appUpdateCheckTick + 1) }
    }

    private fun str(id: Int, vararg args: Any): String = localized.getString(id, *args)

    private fun currentProfile(): String =
        _state.value.openSession?.profile?.ifBlank { null }
            ?: _state.value.activeProfile.ifBlank { "default" }

    private fun uniqueQueuedDownloadName(requested: String): String {
        val clean = inferDownloadFileName(requested, requested)
        if (queuedDownloadNames.add(clean)) return clean
        val dot = clean.lastIndexOf('.').takeIf { it > 0 }
        val stem = dot?.let(clean::substring) ?: clean
        val extension = dot?.let { clean.substring(it) }.orEmpty()
        var suffix = System.currentTimeMillis()
        while (true) {
            val candidate = "$stem-$suffix$extension"
            if (queuedDownloadNames.add(candidate)) return candidate
            suffix += 1
        }
    }

    private fun pickProfile(profiles: List<Profile>): String {
        val stored = store.profile
        if (stored.isNotBlank() && profiles.any { it.name == stored }) return stored
        val chosen = profiles.firstOrNull { it.active }?.name
            ?: profiles.firstOrNull()?.name
            ?: "default"
        store.profile = chosen
        return chosen
    }

    private fun <T> launchWork(
        work: suspend () -> T,
        onSuccess: (T) -> Unit,
        onFailure: ((Throwable) -> Unit)? = null,
    ) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { work() } }
                .onSuccess {
                    _state.update { state -> state.copy(busy = false) }
                    onSuccess(it)
                }
                .onFailure { failure ->
                    _state.update { state -> state.copy(busy = false) }
                    if (onFailure != null) onFailure(failure)
                    else _state.update { state -> state.copy(error = failure.readableMessage(localized)) }
                }
        }
    }

    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank()) return null
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return runCatching { java.net.URL(withScheme) }.map { withScheme }.getOrNull()
    }
}

private val STUDIO_CONFIG_GROUPS = setOf(
    SettingsGroup.Display,
    SettingsGroup.Proxy,
    SettingsGroup.Memory,
    SettingsGroup.Compression,
    SettingsGroup.Sessions,
    SettingsGroup.Privacy,
)

private data class CronEditorData(
    val job: CronJob?,
    val models: List<ModelOption>,
    val skills: List<String>,
    val targets: List<CronDeliveryTarget>,
)

private fun List<CronJob>.upsert(job: CronJob): List<CronJob> =
    (filterNot { it.id == job.id } + job).sortedBy { it.name.lowercase() }

private val INVITE_ALPHABET = ('A'..'Z') + ('2'..'9')

/** Official mobile limits: 9 files per message, 20 MB each. */
internal const val MAX_ATTACHMENTS = 9
internal const val MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024

internal fun Throwable.invalidatesSavedSession(): Boolean =
    (this as? HermesException)?.statusCode in setOf(401, 403)

private fun Throwable.readableMessage(context: android.content.Context): String = when (this) {
    // A HermesException already carries what the server said, in its own words.
    is HermesException -> message ?: context.getString(R.string.error_request_failed)
    is java.net.UnknownHostException -> context.getString(R.string.error_unreachable)
    is java.net.SocketTimeoutException -> context.getString(R.string.error_timeout)
    is javax.net.ssl.SSLException -> context.getString(R.string.error_tls)
    else -> message ?: this::class.java.simpleName
}
