"""Verify original APK evidence; read-only source, local report output."""
from pathlib import Path
import json, re, hashlib

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / 'docs'
x = json.loads((DOC / 'original-app-ui-evidence.json').read_text())
www = Path(x['baseline']['www_root'])
js = (www / 'app-service.js').read_text()
markers = list(re.finditer(r'\[\["__scopeId","data-v-([0-9a-f]+)"\]\]', js))
names = list(re.finditer(r'__name:"([^"]+)"', js))
by_scope = {}
for i, marker in enumerate(markers):
    floor = markers[i-1].end() if i else 0
    candidates = [m for m in names if floor <= m.start() < marker.start()]
    # The scope marker closes the component; it does not start the next one.
    start = candidates[-1].start() if candidates else floor
    by_scope[marker.group(1)] = (start, marker.end(), candidates[-1].group(1) if candidates else None)

for rel, info in x['baseline']['files'].items():
    raw = (www / info['path']).read_bytes()
    assert hashlib.sha256(raw).hexdigest() == info['sha256'], rel
    info['bytes'] = len(raw)

checked = 0
for d in x['domains']:
    d['note_from_unreviewed_draft'] = d.pop('note', '')
    d['credibility'] = 'static anchors verified; runtime/navigation unverified'
    evidence = []
    for scope in d.get('js_scopes', []):
        if scope not in by_scope:
            continue
        a, b, name = by_scope[scope]
        fragment = js[a:b]
        evidence.append({
            'scope': 'data-v-' + scope, 'component_name_candidate': name,
            'char_span': [a,b], 'utf8_byte_span': [len(js[:a].encode()),len(js[:b].encode())],
            'boundary_method': 'nearest preceding __name after previous scope marker to closing scope marker; verify nesting and call graph before using',
            'labels': list(dict.fromkeys(re.findall(r'"([一-鿿][^"\n]{0,40})"',fragment)))[:16],
            'api_paths': sorted(set(re.findall(r'/api/[\w/${}.\-]{2,80}', fragment))),
            'runtime_reachability': 'not verified',
        })
    d['js_evidence'] = evidence
    style = d.get('style')
    if style:
        css = (www/style).read_text()
        for anchor in d.get('css_anchors',[]):
            offset, selector = anchor['offset'], anchor['selector']
            assert css.startswith(selector, offset), (d['id'],offset,selector)
            end = css.index('}',offset)
            actual_decl = css[css.index('{',offset)+1:end]
            assert actual_decl == anchor['decl'], (d['id'], selector)
            anchor['offset_unit'] = 'unicode_codepoint'
            anchor['utf8_byte_offset'] = len(css[:offset].encode())
            checked += 1
    if d['id'] == 'group_chat':
        d['cloud_exclusions'] = []
        d['cloud_review'] = 'Local /api/studio/group-chat routes are not evidence of cloud dependency. Retain local group chat; classify cloud-only discovery/social separately.'
    if d['status'] == 'not_located':
        d['interpretation'] = 'not located by current search; not proof of absence or permission to remove functionality'

x['verification'] = {'css_anchors_checked':checked,'domain_count':len(x['domains']),
 'domain_ids_unique':len({d['id'] for d in x['domains']}) == len(x['domains']),
 'located_count':sum(d['status']=='located' for d in x['domains']),
 'not_located_count':sum(d['status']=='not_located' for d in x['domains']),
 'method_warning':'Scope closing markers corrected. Component attribution and UI reachability require call-graph verification; no execution/visual verification implied.'}
x['component_conflicts'] = [{'topic':'multiple message CSS scopes','scopes':['c0c550c3','8aca294f'], 'resolution':'Do not infer invocation or merge CSS solely from scope presence. Prior draft width and input-reference attribution are unverified.'}]
(DOC/'original-app-ui-evidence.json').write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(x['verification'],ensure_ascii=False,indent=2))
for domain in ['devices','files_workspace','chat','group_chat']:
 d=next(d for d in x['domains'] if d['id']==domain)
 print(domain,[(r['component_name_candidate'],r['char_span']) for r in d['js_evidence']])
