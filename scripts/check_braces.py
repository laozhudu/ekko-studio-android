import subprocess
import sys

FILES = [
    'MainActivity.kt',
    'AppViewModel.kt',
    'HermesApi.kt',
    'RoomSettingsScreen.kt',
    'RoomFilesScreen.kt',
    'AgentAvatar.kt',
    'ChatMarkdown.kt',
    'ChatSocket.kt',
    'UsageScreens.kt',
    'MarkdownMedia.kt',
]


def mask(src):
    """Blank out comments and every string/char literal, keeping offsets.

    Skips normal strings as well as raw ones — `"image/*"` used to open a fake
    block comment in the older counter and swallowed the rest of the file.
    """
    out = []
    i = 0
    n = len(src)
    while i < n:
        if src.startswith('//', i):
            j = src.find('\n', i)
            j = n if j < 0 else j
            out.append(' ' * (j - i))
            i = j
        elif src.startswith('/*', i):
            j = src.find('*/', i + 2)
            j = n - 2 if j < 0 else j
            out.append(' ' * (j + 2 - i))
            i = j + 2
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n - 3 if j < 0 else j
            out.append(' ' * (j + 3 - i))
            i = j + 3
        elif src[i] == '"':
            j = i + 1
            while j < n:
                if src[j] == '\\':
                    j += 2
                    continue
                if src[j] == '"':
                    j += 1
                    break
                j += 1
            out.append(' ' * (j - i))
            i = j
        elif src[i] == "'":
            j = i + 1
            while j < n:
                if src[j] == '\\':
                    j += 2
                    continue
                if src[j] == "'":
                    j += 1
                    break
                j += 1
            out.append(' ' * (j - i))
            i = j
        else:
            out.append(src[i])
            i += 1
    return ''.join(out)


def delta(src):
    m = mask(src)
    return m.count('{') - m.count('}')


base = 'android/app/src/main/java/com/laozhudu/ekkostudio/'
bad = 0
for f in FILES:
    p = base + f
    try:
        new = open(p).read()
    except FileNotFoundError:
        continue
    old = subprocess.run(['git', 'show', 'HEAD:' + p], capture_output=True, text=True).stdout
    before = delta(old) if old else None
    after = delta(new)
    ok = 'OK' if after == 0 or after == before else 'REGRESSED'
    if ok == 'REGRESSED':
        bad += 1
    print(f'{f:24} head={before if before is not None else "(new)":>4} now={after:>3}  {ok}')

sys.exit(1 if bad else 0)
