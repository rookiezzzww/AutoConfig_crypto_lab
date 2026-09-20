const api = async (path, options = {}) => {
  const response = await fetch(path, options);
  const body = await response.json();
  if (!response.ok || !body.success) throw new Error(body.message || '请求失败');
  return body.data;
};
const el = id => document.getElementById(id);
function renderOptions(scenario) {
  if (!scenario.vulnerabilityOptions?.length) return '未声明启动参数';
  return scenario.vulnerabilityOptions.map(option => {
    const condition = option.vulnerabilityConditionMet ? '漏洞条件已满足' : '对照配置';
    return `<div class="option"><span>${option.name}</span><code>${option.effectiveValue}</code><em class="${option.vulnerabilityConditionMet ? 'vulnerable' : 'control'}">${condition}</em></div>`;
  }).join('');
}
async function refresh() {
  try {
    const [items, current, system, logs] = await Promise.all([api('/api/scenarios'), api('/api/scenarios/current'), api('/api/system/status'), api('/api/audit/logs')]);
    el('current').textContent = `当前场景：${current.name} (${current.cve})`;
    const box = el('scenarios'); box.textContent = ''; const template = el('card');
    for (const scenario of items) {
      const node = template.content.cloneNode(true); const article = node.querySelector('article');
      if (scenario.active) article.classList.add('active');
      node.querySelector('.badge').textContent = scenario.active ? 'ACTIVE' : 'STANDBY';
      node.querySelector('h3').textContent = scenario.name; node.querySelector('.cve').textContent = scenario.cve;
      node.querySelector('.description').textContent = scenario.description; node.querySelector('.meta').textContent = scenario.category.join(' · ');
      node.querySelector('.states').textContent = `容器：${scenario.health} ｜ HAProxy：${scenario.haproxyStatus} ｜ 状态：${scenario.status}`;
      node.querySelector('.options').innerHTML = renderOptions(scenario);
      const button = node.querySelector('button'); button.textContent = scenario.active ? '当前已激活' : '切换到该环境';
      button.disabled = scenario.active || scenario.status === 'UNHEALTHY' || !scenario.enabled; button.onclick = () => activate(scenario.id, button); box.append(node);
    }
    el('system').textContent = JSON.stringify(system, null, 2);
    el('logs').innerHTML = logs.length ? logs.map(log => `<div class="log"><time>${new Date(log.timestamp).toLocaleTimeString()}</time><span>${log.previousScenario || '—'} → ${log.targetScenario || '—'}</span><b class="${log.result === 'SUCCESS' ? 'ok' : 'bad'}">${log.result}</b></div>`).join('') : '暂无审计记录';
  } catch (error) { el('current').textContent = '状态读取失败：' + error.message; }
}
async function activate(id, button) {
  button.disabled = true; button.textContent = '切换中…';
  try { const result = await api(`/api/scenarios/${encodeURIComponent(id)}/activate`, { method: 'POST' }); alert(result.message); }
  catch (error) { alert('切换失败：' + error.message); }
  finally { await refresh(); }
}
refresh(); setInterval(refresh, 5000);
