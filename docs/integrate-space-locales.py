import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
fragment = json.loads((root / 'docs/space-map-lang.json').read_text(encoding='utf-8'))
keys = {
'browse_bodies': ('切换天体', 'Browse bodies'),
'preview_banner': ('UI预览 · 示例数据 · 未接入任务结算', 'UI preview · Sample data · Missions offline'),
'inventory': ('物品栏', 'Inventory'),
'controls': ('拖动平移 / 滚轮缩放 / 点击跟随', 'Drag / Scroll to zoom / Click to follow'),
'preview_stage': ('预览阶段：%s', 'Preview: %s'),
'device_tier': ('预览器材：T%s', 'Preview gear: T%s'),
'body_tier': ('任务等级 T%s', 'Mission tier T%s'),
'radius': ('半径 %s km', 'Radius %s km'),
'temperature': ('温度 %s K', 'Temperature %s K'),
'physical_hidden': ('物理数据未解锁', 'Physical data hidden'),
'survey_required': ('需要先测绘', 'Survey required'),
'structure_missing': ('结构未完成', 'Structure incomplete'),
'stage.unknown': ('未测绘', 'Unknown'),
'stage.surveyed': ('已测绘', 'Surveyed'),
'stage.landed': ('已登陆', 'Landed'),
'stage.outpost': ('永久据点', 'Outpost'),
'inputs': ('任务输入', 'Mission inputs'),
'outputs': ('任务输出', 'Mission outputs'),
'modules': ('模块 %s/%s', 'Modules %s/%s'),
'assembler': ('组装模块', 'Assembler'),
'miner': ('采矿模块', 'Mining module'),
'no_stage': ('无开拓任务', 'No mission'),
'action.survey': ('发射测绘任务', 'Launch survey'),
'action.land': ('发射登陆任务', 'Launch landing'),
'action.outpost': ('修建永久据点', 'Build outpost'),
'action.mining': ('启用自动采矿', 'Start mining'),
'display_only': ('无可用阶段', 'No available stage'),
'backend_pending': ('任务系统尚未接通', 'Missions not connected'),
'resource_pool': ('可选产出池', 'Resource pool'),
'selected_count': ('已选 %s/3', 'Selected %s/3'),
'resources_hidden': ('测绘后可见资源（预览）', 'Survey to reveal (preview)'),
'no_resources': ('没有采矿资源', 'No mining resources'),
'mission_status': ('任务状态', 'Mission status'),
'tier_insufficient': ('器材等级不足', 'Tier too low'),
'landing_required': ('需要先登陆', 'Landing required'),
'select_resources': ('请选择开采资源', 'Choose resources'),
'outpost_mode': ('固定据点采矿', 'Outpost mining'),
'long_range_mode': ('长程采矿', 'Long-range mining'),
'output_factor': ('产量 ×%s', 'Yield ×%s'),
'time_factor': ('耗时 ×%s', 'Time ×%s'),
'preview_target': ('预览采矿目标', 'Preview target'),
'focus_target': ('设为预览目标', 'Preview target'),
'legacy_processing': ('原配方加工仍保留', 'Legacy recipes active'),
}
for locale, col in [('zh_cn', 0), ('en_us', 1)]:
    path = root / f'src/main/resources/assets/techreborn/lang/{locale}.json'
    original = path.read_text(encoding='utf-8')
    current = json.loads(original)
    entries = dict(fragment[locale])
    entries.update({'gui.techreborn.space.' + key: value[col] for key, value in keys.items()})
    additions = {k: v for k, v in entries.items() if k not in current}
    insert = '\n'.join('  ' + json.dumps(k, ensure_ascii=False) + ': ' + json.dumps(v, ensure_ascii=False) + ',' for k, v in additions.items())
    # Preserve the existing file and duplicate comment keys; only insert new entries.
    path.write_text(original.replace('{', '{\n' + insert, 1), encoding='utf-8')
    json.loads(path.read_text(encoding='utf-8'))
    print(locale, len(additions), 'UI keys inserted')
