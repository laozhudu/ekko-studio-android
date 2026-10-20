package com.laozhudu.ekkostudio

/**
 * Slash commands the app offers as tappable rows.
 *
 * The names are Studio's own allow-list (it is a plain Set in the server bundle,
 * no endpoint publishes it):
 *
 *     abort bundles clear compress create destroy fork goal learn moa plan queue
 *     reload-mcp reload-skills skill status steer subgoal title usage yolo
 *
 * Anything outside that set is silently ignored by the runner, so the catalog
 * must not be extended with invented commands. [usage] is what gets typed into
 * the composer, so arguments stay editable by the user.
 */
object SlashCommands {

    val all: List<SlashCommand> = listOf(
        SlashCommand("fork", "/fork", R.string.slash_fork),
        SlashCommand("compress", "/compress", R.string.slash_compress),
        SlashCommand("clear", "/clear", R.string.slash_clear),
        SlashCommand("status", "/status", R.string.slash_status),
        SlashCommand("usage", "/usage", R.string.slash_usage),
        SlashCommand("title", "/title ", R.string.slash_title),
        SlashCommand("plan", "/plan", R.string.slash_plan),
        SlashCommand("goal", "/goal ", R.string.slash_goal),
        SlashCommand("subgoal", "/subgoal ", R.string.slash_subgoal),
        SlashCommand("steer", "/steer ", R.string.slash_steer),
        SlashCommand("queue", "/queue ", R.string.slash_queue),
        SlashCommand("skill", "/skill ", R.string.slash_skill),
        SlashCommand("bundles", "/bundles", R.string.slash_bundles),
        SlashCommand("learn", "/learn", R.string.slash_learn),
        SlashCommand("moa", "/moa ", R.string.slash_moa),
        SlashCommand("reload-mcp", "/reload-mcp", R.string.slash_reload_mcp),
        SlashCommand("reload-skills", "/reload-skills", R.string.slash_reload_skills),
        SlashCommand("create", "/create ", R.string.slash_create),
        SlashCommand("destroy", "/destroy ", R.string.slash_destroy),
        SlashCommand("abort", "/abort", R.string.slash_abort),
        SlashCommand("yolo", "/yolo", R.string.slash_yolo),
    )

    /** Rows the composer shows; the destructive ones stay available but last. */
    val suggestions: List<SlashCommand> = all.filterNot { it.name == "destroy" }
}
