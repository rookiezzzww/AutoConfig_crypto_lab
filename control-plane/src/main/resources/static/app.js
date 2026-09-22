/** 保存后端错误码和 HTTP 状态，便于弹窗区分配置错误与启动错误。 */
class ApiError extends Error {
  constructor(message, code, status) {
    super(message);
    this.code = code;
    this.status = status;
  }
}

/** 调用统一 REST 响应接口，并将失败响应转换成 ApiError。 */
const api = async (path, options = {}) => {
  const response = await fetch(path, options);
  let body;
  try {
    body = await response.json();
  } catch (_error) {
    throw new ApiError(`服务器返回了无法解析的响应（HTTP ${response.status}）`, 'INVALID_RESPONSE', response.status);
  }
  if (!response.ok || !body.success)
    throw new ApiError(body.message || '请求失败', body.code || 'REQUEST_FAILED', response.status);
  return body.data;
};

const el = id => document.getElementById(id);
const state = { scenarios: [], current: null, selected: null, submitting: false };

/** 将后端状态枚举转换为中文界面文本。 */
const statusText = status => ({
  STOPPED: '未启动',
  INITIALIZING: '初始化中',
  STANDBY: '运行中（未接流量）',
  ACTIVE: '当前场景',
  UNHEALTHY: '运行异常',
  ERROR: '错误',
  UNKNOWN: '状态未知'
}[status] || status);

/** 渲染场景卡片中的实际生效参数和漏洞条件。 */
function renderOptions(scenario) {
  if (!scenario.vulnerabilityOptions?.length)
    return '<p class="empty-options">该场景没有声明可配置项</p>';
  return scenario.vulnerabilityOptions.map(option => {
    const condition = option.vulnerabilityConditionMet ? '满足漏洞条件' : '对照配置';
    const value = option.effectiveValue ?? '尚未配置';
    return `<div class="option">
      <span>${escapeHtml(option.name)}</span>
      <code>${escapeHtml(value)}</code>
      <em class="${option.vulnerabilityConditionMet ? 'vulnerable' : 'control'}">${condition}</em>
    </div>`;
  }).join('');
}

/** 并行刷新场景、当前路由、系统状态和审计日志。 */
async function refresh() {
  try {
    const [items, current, system, logs] = await Promise.all([
      api('/api/scenarios'),
      api('/api/scenarios/current'),
      api('/api/system/status'),
      api('/api/audit/logs')
    ]);
    state.scenarios = items;
    state.current = current;
    el('current').textContent = current
      ? `当前场景：${current.name}（${current.cve}）`
      : '当前没有已激活的漏洞场景';

    const box = el('scenarios');
    box.textContent = '';
    const template = el('card');
    for (const scenario of items) {
      const node = template.content.cloneNode(true);
      const article = node.querySelector('article');
      if (scenario.active) article.classList.add('active');
      article.dataset.status = scenario.status;
      node.querySelector('.badge').textContent = statusText(scenario.status);
      node.querySelector('h3').textContent = scenario.name;
      node.querySelector('.cve').textContent = scenario.cve;
      node.querySelector('.description').textContent = scenario.description;
      node.querySelector('.meta').textContent = scenario.category.join(' · ');
      node.querySelector('.states').textContent =
        `容器：${scenario.health} ｜ HAProxy：${scenario.haproxyStatus} ｜ ${statusText(scenario.status)}`;
      node.querySelector('.options').innerHTML = renderOptions(scenario);
      const button = node.querySelector('button');
      button.textContent = scenario.active ? '当前场景已激活' : scenario.status === 'STOPPED' ? '配置并启动' : '配置并切换';
      button.disabled = scenario.active || scenario.status === 'INITIALIZING' || !scenario.enabled || state.submitting;
      button.onclick = () => openConfiguration(scenario.id);
      box.append(node);
    }

    el('system').textContent = JSON.stringify(system, null, 2);
    el('logs').innerHTML = logs.length
      ? logs.map(log => `<div class="log">
          <time>${new Date(log.timestamp).toLocaleString()}</time>
          <span>${escapeHtml(log.previousScenario || '无')} → ${escapeHtml(log.targetScenario || '无')}</span>
          <b class="${log.result === 'SUCCESS' ? 'ok' : 'bad'}">${escapeHtml(log.result)}</b>
        </div>`).join('')
      : '暂无审计记录';
  } catch (error) {
    el('current').textContent = '状态读取失败：' + error.message;
  }
}

/** 根据场景元数据生成配置控件并打开初始化弹窗。 */
function openConfiguration(id) {
  const scenario = state.scenarios.find(item => item.id === id);
  if (!scenario) return;
  state.selected = scenario;
  el('dialog-title').textContent = `初始化 ${scenario.name}`;
  el('dialog-summary').textContent = `${scenario.cve} · ${scenario.description}`;
  el('config-error').hidden = true;
  el('config-progress').hidden = true;

  const fields = el('config-fields');
  fields.textContent = '';
  for (const option of scenario.vulnerabilityOptions || []) {
    const wrapper = document.createElement('label');
    wrapper.className = 'field';
    const title = document.createElement('span');
    title.className = 'field-label';
    title.textContent = option.name;
    const description = document.createElement('small');
    description.textContent = option.description || '';
    let input;
    if (option.type?.toLowerCase() === 'enum' && option.allowedValues?.length) {
      input = document.createElement('select');
      for (const allowed of option.allowedValues) {
        const item = document.createElement('option');
        item.value = allowed;
        item.textContent = allowed;
        item.selected = allowed === option.effectiveValue;
        input.append(item);
      }
    } else {
      input = document.createElement('input');
      input.type = option.sensitive ? 'password' : 'text';
      input.value = option.sensitive ? '' : (option.effectiveValue || '');
    }
    input.dataset.optionKey = option.key;
    input.required = true;
    wrapper.append(title, description, input);
    fields.append(wrapper);
  }

  const switching = state.current && state.current.id !== scenario.id;
  el('stop-previous-row').hidden = !switching;
  el('stop-previous').checked = true;
  el('dialog-submit').textContent = switching ? '提交并切换' : '提交并启动';
  el('config-dialog').showModal();
}

/** 收集弹窗配置、执行客户端校验并提交按需启动请求。 */
async function submitConfiguration(event) {
  event.preventDefault();
  if (!state.selected || state.submitting) return;
  const form = el('config-form');
  if (!form.reportValidity()) return;

  const options = {};
  for (const input of el('config-fields').querySelectorAll('[data-option-key]')) {
    const value = input.value.trim();
    if (!value) {
      showConfigurationError(`配置项“${input.closest('.field').querySelector('.field-label').textContent}”不能为空`);
      input.focus();
      return;
    }
    options[input.dataset.optionKey] = value;
  }

  state.submitting = true;
  setDialogBusy(true);
  try {
    const result = await api(`/api/scenarios/${encodeURIComponent(state.selected.id)}/activate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ options, stopPrevious: el('stop-previous').checked })
    });
    el('config-dialog').close();
    window.alert(result.message);
  } catch (error) {
    const prefix = error.code === 'INVALID_SCENARIO_CONFIGURATION' ? '配置项填写错误：' : '场景启动失败：';
    showConfigurationError(prefix + error.message);
  } finally {
    state.submitting = false;
    setDialogBusy(false);
    await refresh();
  }
}

/** 在弹窗内显示可修改后重试的配置或启动错误。 */
function showConfigurationError(message) {
  const box = el('config-error');
  box.textContent = message;
  box.hidden = false;
}

/** 在后台初始化期间锁定表单，防止重复提交。 */
function setDialogBusy(busy) {
  el('dialog-submit').disabled = busy;
  el('dialog-cancel').disabled = busy;
  el('dialog-close').disabled = busy;
  el('config-progress').hidden = !busy;
  for (const input of el('config-fields').querySelectorAll('input, select')) input.disabled = busy;
  el('stop-previous').disabled = busy;
}

/** 仅在没有提交任务时关闭弹窗。 */
function closeDialog() {
  if (!state.submitting) el('config-dialog').close();
}

/** 转义进入 innerHTML 的审计和参数文本。 */
function escapeHtml(value) {
  return String(value).replace(/[&<>'"]/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
  }[character]));
}

el('config-form').addEventListener('submit', submitConfiguration);
el('dialog-close').addEventListener('click', closeDialog);
el('dialog-cancel').addEventListener('click', closeDialog);
el('config-dialog').addEventListener('cancel', event => {
  if (state.submitting) event.preventDefault();
});

refresh();
setInterval(() => {
  if (!state.submitting) refresh();
}, 5000);
