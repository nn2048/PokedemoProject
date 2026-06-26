#!/usr/bin/env python3
import csv, json, pathlib

root = pathlib.Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources' / 'assets' / 'pokedemo_bridge' / 'cobblemon'
out = pathlib.Path(__file__).resolve().parents[1] / 'ACTION_COVERAGE_REPORT.csv'
rows=[]
for species_dir in sorted([p for p in root.iterdir() if p.is_dir()]):
    key=species_dir.name
    anim=species_dir / f'{key}.animation.json'
    poser=species_dir / f'{key}.poser.json'
    clip_names=[]
    if anim.exists():
        try:
            data=json.loads(anim.read_text(encoding='utf-8')).get('animations',{})
            clip_names=list(data.keys())
        except Exception:
            pass
    clip_tails={c.split('.')[-1].lower().replace('-','_') for c in clip_names}
    def has_any(*names):
        for n in names:
            if n in clip_tails:
                return True
        return False
    rows.append({
        'species': key,
        'has_poser_json': poser.exists(),
        'has_anim_json': anim.exists(),
        'idle': has_any('ground_idle','idle','air_idle','water_idle','surfacewater_idle'),
        'walk': has_any('ground_walk','walk','ground_walkfast','move','moving'),
        'run': has_any('run','sprint','ground_run'),
        'fly': has_any('air_fly','fly','glide','flying'),
        'hover': has_any('air_idle','hover','fly_idle'),
        'swim': has_any('water_swim','surfacewater_swim','swim','water_swimfast','surfacewater_swimfast'),
        'float': has_any('water_idle','surfacewater_idle','float','surface_idle'),
        'sleep': has_any('sleep','ground_sleep','air_sleep','water_sleep','surfacewater_sleep'),
        'physical': has_any('physical','attack_physical','attack','melee'),
        'special': has_any('special','attack_special','beam','ranged'),
        'status': has_any('status','attack_status','cast','support'),
        'cry': has_any('cry'),
        'recoil': has_any('recoil','hurt','hit','damage'),
        'clips': ';'.join(sorted(clip_tails)),
    })
with out.open('w',newline='',encoding='utf-8') as f:
    writer=csv.DictWriter(f, fieldnames=list(rows[0].keys()))
    writer.writeheader(); writer.writerows(rows)
print(f'wrote {out}')
