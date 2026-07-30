const state = {
  user: null,
  view: "dashboard",
  selectedUser: null,
  knowledgePath: null,
  personasCache: [],
  usersCache: [],
  memoriesCache: [],
  messagePollTimer: null,
};

function can(perm) {
  const perms = (state.user && state.user.permissions) || [];
  return perms.includes(perm);
}

function canAny(...perms) {
  return perms.some((p) => can(p));
}

async function api(path, opts = {}) {
  const res = await fetch(path, {
    credentials: "include",
    headers: { "Content-Type": "application/json", ...(opts.headers || {}) },
    ...opts,
  });
  let data = null;
  const text = await res.text();
  try { data = text ? JSON.parse(text) : null; } catch { data = { detail: text }; }
  if (!res.ok) {
    const msg = (data && (data.detail || data.message)) || res.statusText;
    throw new Error(typeof msg === "string" ? msg : JSON.stringify(msg));
  }
  return data;
}

function $(sel) { return document.querySelector(sel); }
function el(html) {
  const t = document.createElement("template");
  t.innerHTML = html.trim();
  return t.content.firstChild;
}
function esc(s) {
  return String(s ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}
function debounce(fn, ms) {
  let t;
  return (...args) => {
    clearTimeout(t);
    t = setTimeout(() => fn(...args), ms);
  };
}

function fmtTime(value) {
  if (!value) return "—";
  const raw = String(value);
  const normalized = raw.includes("T") ? raw : raw.replace(" ", "T") + "Z";
  const d = new Date(normalized);
  return Number.isNaN(d.getTime()) ? raw : d.toLocaleString();
}
function fmtBytes(value) {
  const bytes = Number(value) || 0;
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KiB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MiB`;
}
function outcomeLabel(value) {
  return ({ success: "成功", denied: "拒绝", rejected: "未接受", error: "错误" })[value] || value || "未知";
}
function messageStatusLabel(value) {
  return ({
    pending: "待处理",
    processing: "草稿生成中",
    draft_ready: "草稿已生成",
    sending: "投递确认中",
    sent: "已发送",
    failed: "失败",
    uncertain: "投递状态不确定",
    cancelled: "已取消",
  })[value] || value || "未知";
}
function fmtMessageTime(value) {
  const numeric = Number(value);
  if (Number.isFinite(numeric) && numeric > 0) {
    const date = new Date(numeric < 1e12 ? numeric * 1000 : numeric);
    if (!Number.isNaN(date.getTime())) return date.toLocaleString();
  }
  return fmtTime(value);
}

function showLogin() {
  $("#login-view").classList.remove("hidden");
  $("#main-view").classList.add("hidden");
}

function showMain() {
  $("#login-view").classList.add("hidden");
  $("#main-view").classList.remove("hidden");
  const role = state.user.role || "";
  $("#whoami").textContent = `${state.user.username} · ${role}`;
  document.querySelectorAll("#nav button[data-perm]").forEach((n) => {
    const needed = (n.dataset.perm || "").split(",").map((s) => s.trim()).filter(Boolean);
    n.classList.toggle("hidden", needed.length > 0 && !canAny(...needed));
  });
  const currentBtn = document.querySelector(`#nav button[data-view="${state.view}"]`);
  if (!currentBtn || currentBtn.classList.contains("hidden")) {
    const firstVisible = [...document.querySelectorAll("#nav button")].find((b) => !b.classList.contains("hidden"));
    if (firstVisible) state.view = firstVisible.dataset.view;
  }
  navigate(state.view);
}

async function boot() {
  try {
    state.user = await api("/api/auth/me");
    showMain();
  } catch {
    showLogin();
  }
}

$("#login-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  $("#login-error").textContent = "";
  try {
    state.user = await api("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({
        username: $("#login-user").value.trim(),
        password: $("#login-pass").value,
      }),
    });
    showMain();
  } catch (err) {
    $("#login-error").textContent = err.message;
  }
});

$("#logout-btn").addEventListener("click", async () => {
  await api("/api/auth/logout", { method: "POST" });
  state.user = null;
  showLogin();
});

document.querySelectorAll("#nav button").forEach((btn) => {
  btn.addEventListener("click", () => navigate(btn.dataset.view));
});

function setActiveNav(view) {
  document.querySelectorAll("#nav button").forEach((b) => {
    b.classList.toggle("active", b.dataset.view === view);
  });
}

const titles = {
  dashboard: "概览",
  personas: "人设库",
  behavior: "Bot 行为",
  users: "Bot 用户",
  memories: "记忆库",
  messages: "消息中心",
  config: "高级配置",
  knowledge: "资料站",
  ops: "运维",
  audit: "审计与备份",
  account: "我的账号",
  "console-users": "账号与权限",
};

async function navigate(view) {
  if (state.messagePollTimer) {
    clearTimeout(state.messagePollTimer);
    state.messagePollTimer = null;
  }
  state.view = view;
  setActiveNav(view);
  $("#view-title").textContent = titles[view] || view;
  $("#top-actions").innerHTML = "";
  const root = $("#view-root");
  root.innerHTML = "<p class='muted'>加载中…</p>";
  try {
    if (view === "dashboard") await renderDashboard(root);
    else if (view === "personas") await renderPersonas(root);
    else if (view === "behavior") await renderBehavior(root);
    else if (view === "users") await renderUsers(root);
    else if (view === "memories") await renderMemories(root);
    else if (view === "messages") await renderMessages(root);
    else if (view === "config") await renderConfig(root);
    else if (view === "knowledge") await renderKnowledge(root);
    else if (view === "ops") await renderOps(root);
    else if (view === "audit") await renderAuditBackups(root);
    else if (view === "account") await renderAccount(root);
    else if (view === "console-users") await renderConsoleUsers(root);
  } catch (err) {
    root.innerHTML = `<p class="error">${esc(err.message)}</p>`;
  }
}

async function renderDashboard(root) {
  const stats = await api("/api/stats");
  root.innerHTML = "";
  root.appendChild(el(`
    <div class="cards">
      <div class="card"><div class="muted">共享人设</div><div class="n">${stats.personas ?? 0}</div></div>
      <div class="card"><div class="muted">Companion 用户</div><div class="n">${stats.companion_users}</div></div>
      <div class="card"><div class="muted">记忆条目</div><div class="n">${stats.memories ?? 0}</div></div>
      <div class="card"><div class="muted">资料文档</div><div class="n">${stats.knowledge_docs}</div></div>
    </div>
  `));
  root.appendChild(el(`
    <div class="panel" style="margin-top:16px">
      <h3>这是人设库 / 记忆库后台</h3>
      <p class="muted">Persona Lib 是身份、别名、人设和任意属性的唯一共享来源；旧 SoulMap 只作为兼容数据展示。</p>
      <p class="muted">「Bot 行为」统一管理 tt 路由、人格、自动回复、联网搜索、识图和生图提示词合规改写。</p>
    </div>
  `));
}

/* ===================== 人设库 ===================== */
async function renderPersonas(root) {
  const canEdit = can("personas.edit");
  $("#top-actions").innerHTML = "";
  if (canEdit) {
    const create = el(`<button type="button">+ 新建人设</button>`);
    create.onclick = async () => {
      const subjectId = prompt("稳定 ID（推荐填 QQ openid；没有时可填 name:名称）");
      if (!subjectId) return;
      const alias = prompt("主要名称 / 别名") || "";
      try {
        await api(`/api/personas/${encodeURIComponent(subjectId.trim())}`, {
          method: "PUT",
          body: JSON.stringify({ fields: { kind: "persona", scope: "shared", names: alias ? [alias.trim()] : [] } }),
        });
        await renderPersonas(root);
      } catch (e) { alert(e.message); }
    };
    const exportBtn = el(`<button type="button" class="ghost">导出 JSON</button>`);
    exportBtn.onclick = async () => {
      const data = await api("/api/personas/export/all");
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = `textech-personas-${new Date().toISOString().slice(0, 10)}.json`;
      a.click();
      URL.revokeObjectURL(a.href);
    };
    const importInput = el(`<input type="file" accept="application/json,.json" class="hidden" />`);
    const importBtn = el(`<button type="button" class="ghost">导入 JSON</button>`);
    importBtn.onclick = () => importInput.click();
    importInput.onchange = async () => {
      const file = importInput.files && importInput.files[0];
      if (!file) return;
      const replace = confirm("点“确定”将整库替换；点“取消”则合并导入。写入前会自动备份。 ");
      try {
        const data = JSON.parse(await file.text());
        const result = await api("/api/personas/import", {
          method: "POST",
          body: JSON.stringify({ data, mode: replace ? "replace" : "merge" }),
        });
        alert(`已导入 ${result.imported} 条，当前共 ${result.total} 条`);
        await renderPersonas(root);
      } catch (e) { alert(e.message); }
    };
    $("#top-actions").append(create, exportBtn, importBtn, importInput);
  }
  root.innerHTML = "";
  const toolbar = el(`<div class="toolbar">
    <input id="persona-q" placeholder="筛选：稳定 ID / QQ昵称 / 别名 / 标签 / 任意属性…" style="min-width:260px;flex:1" />
    <span class="muted" id="persona-count"></span>
  </div>`);
  const split = el(`<div class="split wide"><div id="persona-list"></div><div id="persona-detail"><p class="muted">选择左侧人设查看或编辑</p></div></div>`);
  root.append(toolbar, split);
  const listBox = split.querySelector("#persona-list");
  const detailBox = split.querySelector("#persona-detail");
  const qInput = toolbar.querySelector("#persona-q");
  const countEl = toolbar.querySelector("#persona-count");

  async function load(q) {
    const data = await api("/api/personas" + (q ? `?q=${encodeURIComponent(q)}` : ""));
    state.personasCache = data.items || [];
    countEl.textContent = `共 ${data.total} 条`;
    listBox.innerHTML = "";
    const table = el(`<div class="table-wrap"><table>
      <thead><tr>
        <th>稳定 ID</th><th>别名</th><th>标签</th><th>属性</th><th>类型/范围</th><th>更新</th>
      </tr></thead><tbody></tbody>
    </table></div>`);
    const tbody = table.querySelector("tbody");
    for (const item of state.personasCache) {
      const persona = item.persona_lib || {};
      const attrs = persona.attributes || {};
      const tr = el(`<tr class="clickable">
        <td class="mono">${esc(item.user_id)}</td>
        <td>${esc((persona.names || item.persona_names || []).join(" / ") || "—")}</td>
        <td>${esc((persona.tags || []).join(" / ") || "—")}</td>
        <td>${Object.keys(attrs).length}</td>
        <td>${esc(persona.kind || "legacy")} / ${esc(persona.scope || "shared")}</td>
        <td class="muted">${esc(persona._last_updated || item.last_updated || "")}</td>
      </tr>`);
      tr.onclick = () => openPersona(item.user_id, detailBox, () => load(qInput.value.trim()));
      tbody.appendChild(tr);
    }
    listBox.appendChild(table);
    if (!state.personasCache.length) {
      listBox.appendChild(el(`<p class="muted">暂无人设（筛选后为空）</p>`));
    }
  }

  qInput.addEventListener("input", debounce(() => load(qInput.value.trim()), 200));
  await load("");
}

async function openPersona(userId, detailBox, reloadList) {
  const data = await api(`/api/personas/${encodeURIComponent(userId)}`);
  const canEdit = can("personas.edit");
  const ident = data.identity || {};
  const legacy = data.profile || {};
  const p = data.persona_lib || {
    kind: "persona", scope: "shared", names: [], tags: [], attributes: {},
    appearance: "", personality: "", content: "", extra: "",
  };

  detailBox.innerHTML = "";
  detailBox.appendChild(el(`
    <div class="panel">
      <h3>稳定身份 ${esc(userId)}</h3>
      <div class="meta-grid">
        <div><span class="muted">稳定 ID / QQ OpenID</span><div class="mono">${esc(ident.qq_openid || userId)}</div></div>
        <div><span class="muted">QQ 昵称</span><div>${esc(ident.qq_display_name || "—")}</div></div>
        <div><span class="muted">Companion 昵称</span><div>${esc(ident.companion_nickname || "—")}</div></div>
        <div><span class="muted">绑定状态</span><div>${p.subject_id ? "已绑定稳定身份" : "名称型记录"}</div></div>
      </div>
    </div>
  `));

  const panel = el(`<div class="panel"><h3>共享人设与预设属性</h3><div class="grid2" id="persona-fields"></div><div id="attr-list"></div><div class="toolbar" id="persona-actions"></div></div>`);
  const fields = panel.querySelector("#persona-fields");
  const controls = {};
  const specs = [
    ["kind", "类型", "select", ["persona", "memory", "knowledge"]],
    ["scope", "范围", "select", ["shared", "private"]],
    ["names", "名称 / 别名（逗号或换行分隔）", "textarea"],
    ["tags", "标签（逗号或换行分隔）", "textarea"],
    ["appearance", "外观", "textarea"],
    ["personality", "性格", "textarea"],
    ["content", "内容 / 事实", "textarea"],
    ["extra", "备注 / 默认画风", "textarea"],
  ];
  for (const [key, label, type, options] of specs) {
    const field = el(`<div class="field"><label>${esc(label)}</label></div>`);
    let input;
    if (type === "select") {
      input = el(`<select ${canEdit ? "" : "disabled"}></select>`);
      options.forEach((option) => input.appendChild(el(`<option value="${esc(option)}">${esc(option)}</option>`)));
    } else {
      input = el(`<textarea rows="3" ${canEdit ? "" : "disabled"}></textarea>`);
    }
    const value = p[key] ?? (key === "names" || key === "tags" ? [] : "");
    input.value = Array.isArray(value) ? value.join("\n") : String(value);
    controls[key] = input;
    field.appendChild(input);
    fields.appendChild(field);
  }

  const attrPanel = panel.querySelector("#attr-list");
  const attrs = { ...(p.attributes || {}) };
  const drawAttrs = () => {
    attrPanel.innerHTML = `<h4>任意属性</h4>`;
    for (const [key, value] of Object.entries(attrs)) {
      const row = el(`<div class="attribute-row"><input class="attr-key" ${canEdit ? "" : "disabled"}/><input class="attr-value" ${canEdit ? "" : "disabled"}/></div>`);
      const keyInput = row.querySelector(".attr-key");
      const valueInput = row.querySelector(".attr-value");
      keyInput.value = key;
      valueInput.value = String(value ?? "");
      if (canEdit) {
        const remove = el(`<button type="button" class="danger">删除</button>`);
        remove.onclick = () => { delete attrs[key]; drawAttrs(); };
        row.appendChild(remove);
        keyInput.onchange = () => {
          const next = keyInput.value.trim();
          if (!next || next === key) return;
          attrs[next] = attrs[key]; delete attrs[key]; drawAttrs();
        };
        valueInput.oninput = () => { attrs[key] = valueInput.value; };
      }
      attrPanel.appendChild(row);
    }
    if (canEdit) {
      const add = el(`<button type="button" class="ghost">+ 添加属性</button>`);
      add.onclick = () => {
        const key = prompt("属性名（例如：种族、发色、服装、默认画风）");
        if (!key || key.trim() in attrs) return;
        attrs[key.trim()] = ""; drawAttrs();
      };
      attrPanel.appendChild(add);
    }
  };
  drawAttrs();

  if (canEdit) {
    const save = el(`<button type="button">保存并立即供 Bot 使用</button>`);
    save.onclick = async () => {
      const splitList = (value) => value.split(/[\n,，、|]+/).map((s) => s.trim()).filter(Boolean);
      const fieldsPatch = {
        kind: controls.kind.value,
        scope: controls.scope.value,
        names: splitList(controls.names.value),
        tags: splitList(controls.tags.value),
        appearance: controls.appearance.value,
        personality: controls.personality.value,
        content: controls.content.value,
        extra: controls.extra.value,
        attributes: Object.fromEntries(Object.entries(attrs).filter(([k, v]) => k.trim() && String(v).trim())),
      };
      try {
        await api(`/api/personas/${encodeURIComponent(userId)}`, { method: "PATCH", body: JSON.stringify({ fields: fieldsPatch }) });
        alert("已保存；Persona Lib 每次请求都会重新载入，无需重启即可用于对话和生图。");
        if (reloadList) await reloadList();
        await openPersona(userId, detailBox, reloadList);
      } catch (e) { alert(e.message); }
    };
    const remove = el(`<button type="button" class="danger">删除这条人设</button>`);
    remove.onclick = async () => {
      if (!confirm(`确认删除 ${userId} 的共享人设？`)) return;
      try {
        await api(`/api/personas/${encodeURIComponent(userId)}`, { method: "DELETE" });
        detailBox.innerHTML = `<p class="muted">已删除</p>`;
        if (reloadList) await reloadList();
      } catch (e) { alert(e.message); }
    };
    panel.querySelector("#persona-actions").append(save, remove);
  }
  detailBox.appendChild(panel);

  if (Object.keys(legacy).length) {
    detailBox.appendChild(el(`<details class="panel"><summary>旧 SoulMap 兼容数据（只读）</summary><pre class="code">${esc(JSON.stringify(legacy, null, 2))}</pre></details>`));
  }
}

/* ===================== Bot 用户 ===================== */
async function renderUsers(root) {
  const canEdit = can("bot_users.edit");
  $("#top-actions").innerHTML = "";
  if (canEdit) {
    const btn = el(`<button type="button">+ 创建用户</button>`);
    btn.onclick = () => showCreateUser();
    $("#top-actions").appendChild(btn);
  }

  const wrap = el(`<div></div>`);
  const toolbar = el(`<div class="toolbar">
    <input id="user-q" placeholder="筛选：openid / QQ昵称 / 人设称呼 / Companion昵称…" style="min-width:280px;flex:1" />
    <span class="muted" id="user-count"></span>
  </div>`);
  const listBox = el(`<div></div>`);
  const detailBox = el(`<div></div>`);
  wrap.append(toolbar, listBox, detailBox);
  root.innerHTML = "";
  root.appendChild(wrap);
  const qInput = toolbar.querySelector("#user-q");
  const countEl = toolbar.querySelector("#user-count");

  async function load(q) {
    const data = await api("/api/bot-users" + (q ? `?q=${encodeURIComponent(q)}` : ""));
    state.usersCache = data.users || [];
    countEl.textContent = `共 ${data.total} 人`;
    listBox.innerHTML = "";
    const table = el(`
      <div class="table-wrap"><table>
        <thead><tr>
          <th>QQ OpenID</th><th>QQ 昵称</th><th>人设称呼</th><th>Companion 昵称</th>
          <th>画像</th><th>启用</th><th>记忆</th><th>照片限额</th>
        </tr></thead><tbody></tbody>
      </table></div>
    `);
    const tbody = table.querySelector("tbody");
    for (const u of state.usersCache) {
      const tr = el(`
        <tr class="clickable">
          <td class="mono">${esc(u.qq_openid || u.user_id)}</td>
          <td>${esc(u.qq_display_name || "—")}</td>
          <td>${esc(u.persona_address || "—")}</td>
          <td>${esc(u.companion_nickname || "—")}</td>
          <td>${u.has_profile ? "✓" : "—"}</td>
          <td><span class="badge ${u.enabled ? "on" : "off"}">${u.enabled ? "on" : "off"}</span></td>
          <td>${u.memory_count ?? 0}</td>
          <td>${u.photo_daily_limit ?? "—"}</td>
        </tr>
      `);
      tr.onclick = () => openUser(u.user_id, detailBox);
      tbody.appendChild(tr);
    }
    listBox.appendChild(table);
  }

  qInput.addEventListener("input", debounce(() => load(qInput.value.trim()), 200));
  await load("");

  async function showCreateUser() {
    const uid = prompt("新用户 ID（QQ openid）");
    if (!uid) return;
    const nickname = prompt("Companion 昵称（可选）") || "";
    try {
      await api("/api/bot-users", {
        method: "POST",
        body: JSON.stringify({
          user_id: uid.trim(),
          companion: { nickname },
          create_profile: true,
        }),
      });
      await load(qInput.value.trim());
      await openUser(uid.trim(), detailBox);
    } catch (err) {
      alert(err.message);
    }
  }
}

async function openUser(userId, detailBox) {
  const data = await api(`/api/bot-users/${encodeURIComponent(userId)}`);
  state.selectedUser = data;
  const canEditBot = can("bot_users.edit");
  const canEditPersona = can("personas.edit");
  const c = data.companion || {};
  const p = data.profile || {};
  const ident = data.identity || data.summary || {};

  detailBox.innerHTML = "";
  detailBox.appendChild(el(`
    <div class="panel">
      <h3>用户 ${esc(userId)}</h3>
      <div class="meta-grid">
        <div><span class="muted">QQ OpenID</span><div class="mono">${esc(ident.qq_openid || userId)}</div></div>
        <div><span class="muted">QQ 昵称</span><div>${esc(ident.qq_display_name || "—")}</div></div>
        <div><span class="muted">人设称呼</span><div>${esc((p && p["对用户的称呼"]) || "—")}</div></div>
        <div><span class="muted">Companion 昵称</span><div>${esc(ident.companion_nickname || c.nickname || "—")}</div></div>
        <div><span class="muted">UMO</span><div class="mono small">${esc(ident.umo || c.umo || "—")}</div></div>
      </div>
    </div>
  `));

  const perm = el(`<div class="panel"><h3>Companion 权限</h3><div class="grid2" id="comp-fields"></div></div>`);
  const fieldsRoot = perm.querySelector("#comp-fields");
  const editable = data.editable_companion_fields || [];
  const values = {};
  for (const key of editable) {
    values[key] = c[key];
    const type = typeof c[key] === "boolean" ? "checkbox" : "text";
    const field = el(`<div class="field"><label>${esc(key)}</label></div>`);
    let input;
    if (type === "checkbox") {
      input = el(`<input type="checkbox" ${c[key] ? "checked" : ""} ${canEditBot ? "" : "disabled"} />`);
      input.onchange = () => { values[key] = input.checked; };
    } else {
      input = el(`<input ${canEditBot ? "" : "disabled"} />`);
      input.value = c[key] == null ? "" : String(c[key]);
      input.oninput = () => {
        const v = input.value;
        values[key] = v === "" ? "" : (isFinite(Number(v)) && String(Number(v)) === v ? Number(v) : v);
      };
    }
    field.appendChild(input);
    fieldsRoot.appendChild(field);
  }
  if (canEditBot) {
    const bar = el(`<div class="toolbar"></div>`);
    const save = el(`<button type="button">保存权限</button>`);
    save.onclick = async () => {
      try {
        await api(`/api/bot-users/${encodeURIComponent(userId)}/companion`, {
          method: "PATCH",
          body: JSON.stringify({ fields: values }),
        });
        alert("已保存");
      } catch (e) { alert(e.message); }
    };
    const disable = el(`<button type="button" class="ghost danger">停用用户</button>`);
    disable.onclick = async () => {
      if (!confirm("确认停用？不会删除记忆。")) return;
      await api(`/api/bot-users/${encodeURIComponent(userId)}/disable`, { method: "POST" });
      await openUser(userId, detailBox);
    };
    bar.append(save, disable);
    perm.appendChild(bar);
  }
  detailBox.appendChild(perm);

  const profilePanel = el(`<div class="panel"><h3>SoulMap 人设</h3><div class="grid2" id="prof-fields"></div></div>`);
  const pf = profilePanel.querySelector("#prof-fields");
  const pvals = {};
  for (const key of data.soulmap_fields || []) {
    pvals[key] = p[key] ?? "";
    const field = el(`<div class="field"><label>${esc(key)}</label></div>`);
    const input = key === "备注"
      ? el(`<textarea rows="4" ${canEditPersona ? "" : "disabled"}></textarea>`)
      : el(`<input ${canEditPersona ? "" : "disabled"} />`);
    input.value = p[key] == null ? "" : String(p[key]);
    input.oninput = () => { pvals[key] = input.value; };
    field.appendChild(input);
    pf.appendChild(field);
  }
  if (canEditPersona) {
    const saveP = el(`<button type="button">保存人设</button>`);
    saveP.onclick = async () => {
      try {
        await api(`/api/bot-users/${encodeURIComponent(userId)}/profile`, {
          method: "PATCH",
          body: JSON.stringify({ fields: pvals }),
        });
        alert("人设已保存");
        await openUser(userId, detailBox);
      } catch (e) { alert(e.message); }
    };
    profilePanel.appendChild(saveP);
  }
  detailBox.appendChild(profilePanel);

  if (can("memories.view")) {
    const mem = await api(`/api/bot-users/${encodeURIComponent(userId)}/memories`);
    const memPanel = el(`<div class="panel"><h3>记忆</h3></div>`);
    const items = (mem.items || []).filter((x) => x.type === "companion_memory");
    if (!items.length) {
      memPanel.appendChild(el(`<p class="muted">暂无 companion_memory 条目</p>`));
    } else {
      for (const it of items) {
        memPanel.appendChild(el(`<div class="mem-card">
          <div class="muted">${esc(it.kind || "")} · weight ${esc(it.weight)} · ${esc(it.created_at || "")}</div>
          <div>${esc(it.text || "")}</div>
        </div>`));
      }
    }
    detailBox.appendChild(memPanel);
  }
}

/* ===================== 记忆库 ===================== */
async function renderMemories(root) {
  const canEdit = can("memories.edit");
  root.innerHTML = "";
  const toolbar = el(`<div class="toolbar">
    <input id="mem-uid" placeholder="筛选 user_id（可选）" style="min-width:180px" />
    <input id="mem-kind" placeholder="kind（可选）" style="min-width:100px" />
    <input id="mem-q" placeholder="关键词筛选…" style="min-width:180px;flex:1" />
    <span class="muted" id="mem-count"></span>
  </div>`);
  if (canEdit) {
    const addBtn = el(`<button type="button">+ 添加记忆</button>`);
    addBtn.onclick = () => showAddMemory();
    toolbar.appendChild(addBtn);
  }
  const listBox = el(`<div id="mem-list"></div>`);
  const sourcesBox = el(`<div class="panel"><h3>数据源</h3><pre class="code" id="mem-sources">…</pre></div>`);
  root.append(toolbar, listBox, sourcesBox);

  const uidInput = toolbar.querySelector("#mem-uid");
  const kindInput = toolbar.querySelector("#mem-kind");
  const qInput = toolbar.querySelector("#mem-q");
  const countEl = toolbar.querySelector("#mem-count");

  async function load() {
    const params = new URLSearchParams();
    if (uidInput.value.trim()) params.set("user_id", uidInput.value.trim());
    if (kindInput.value.trim()) params.set("kind", kindInput.value.trim());
    if (qInput.value.trim()) params.set("q", qInput.value.trim());
    const data = await api("/api/memories?" + params.toString());
    state.memoriesCache = data.items || [];
    countEl.textContent = `共 ${data.total ?? state.memoriesCache.length} 条`;
    sourcesBox.querySelector("#mem-sources").textContent = JSON.stringify(data.sources || [], null, 2);

    listBox.innerHTML = "";
    const editableItems = state.memoriesCache.filter((x) => x.type === "companion_memory" || x.editable);
    const otherItems = state.memoriesCache.filter((x) => !(x.type === "companion_memory" || x.editable));

    if (!editableItems.length && !otherItems.length) {
      listBox.appendChild(el(`<div class="panel"><p class="muted">暂无记忆。可点击「添加记忆」写入 companion_memory。</p></div>`));
      return;
    }

    const table = el(`<div class="table-wrap"><table>
      <thead><tr>
        <th>用户</th><th>QQ 昵称</th><th>称呼/昵称</th><th>kind</th><th>内容</th><th>权重</th><th>时间</th><th>操作</th>
      </tr></thead><tbody></tbody>
    </table></div>`);
    const tbody = table.querySelector("tbody");
    for (const it of editableItems) {
      const tr = el(`<tr>
        <td class="mono small">${esc(it.user_id)}</td>
        <td>${esc(it.qq_display_name || "—")}</td>
        <td>${esc(it.nickname || "—")}</td>
        <td>${esc(it.kind || "")}</td>
        <td class="mem-text">${esc(it.text || "")}</td>
        <td>${esc(it.weight)}</td>
        <td class="muted">${esc(it.created_at || "")}</td>
        <td class="ops-cell"></td>
      </tr>`);
      const cell = tr.querySelector(".ops-cell");
      if (canEdit) {
        const edit = el(`<button type="button" class="ghost">改</button>`);
        edit.onclick = () => editMemory(it);
        const del = el(`<button type="button" class="ghost danger">删</button>`);
        del.onclick = async () => {
          if (!confirm("删除这条记忆？")) return;
          try {
            await api(`/api/memories/${encodeURIComponent(it.user_id)}/${it.index}`, { method: "DELETE" });
            await load();
          } catch (e) { alert(e.message); }
        };
        cell.append(edit, del);
      } else {
        cell.textContent = "—";
      }
      tbody.appendChild(tr);
    }
    listBox.appendChild(table);

    if (otherItems.length) {
      listBox.appendChild(el(`<div class="panel"><h3>其它只读来源</h3>
        <pre class="code">${esc(JSON.stringify(otherItems, null, 2))}</pre></div>`));
    }
  }

  async function showAddMemory() {
    const user_id = prompt("写入哪个 user_id（QQ openid）？");
    if (!user_id) return;
    const text = prompt("记忆内容");
    if (!text) return;
    const kind = prompt("kind（如 preference / note / fact）", "note") || "note";
    try {
      await api("/api/memories", {
        method: "POST",
        body: JSON.stringify({ user_id: user_id.trim(), text, kind }),
      });
      await load();
    } catch (e) { alert(e.message); }
  }

  async function editMemory(it) {
    const text = prompt("内容", it.text || "");
    if (text == null) return;
    const kind = prompt("kind", it.kind || "note");
    if (kind == null) return;
    const weightRaw = prompt("weight", String(it.weight ?? 1));
    if (weightRaw == null) return;
    try {
      await api(`/api/memories/${encodeURIComponent(it.user_id)}/${it.index}`, {
        method: "PATCH",
        body: JSON.stringify({
          text,
          kind,
          weight: Number(weightRaw) || 1,
        }),
      });
      await load();
    } catch (e) { alert(e.message); }
  }

  const reload = debounce(load, 200);
  uidInput.addEventListener("input", reload);
  kindInput.addEventListener("input", reload);
  qInput.addEventListener("input", reload);
  await load();
}

/* ===================== 统一 Bot 行为 ===================== */
async function renderBehavior(root) {
  const data = await api("/api/runtime-profile");
  const canEdit = can("config.edit");
  const routing = data.routing || {}, search = data.search || {}, persona = data.persona || {};
  const auto = data.automatic_reply || {}, image = data.image || {};
  let autoMode = "occasional";
  if (!auto.group_interjection_enabled && !auto.group_followup_enabled) autoMode = "off";
  else if ((auto.group_interject_min_interval_minutes || 999) <= 90 || (auto.group_interject_max_daily || 0) >= 5) autoMode = "frequent";
  root.innerHTML = "";
  const form = el(`<form class="behavior-form">
    <div class="panel"><h3>人格与说话方式</h3><div class="grid2">
      <div class="field"><label>Bot 名称</label><input id="bh-name" ${canEdit ? "" : "disabled"}/></div>
      <div class="field"><label>人格 ID</label><input id="bh-persona" ${canEdit ? "" : "disabled"}/></div></div>
      <div class="field"><label>回复风格</label><textarea id="bh-style" rows="4" ${canEdit ? "" : "disabled"}></textarea></div>
      <div class="field"><label>主对话人格补充</label><textarea id="bh-talk" rows="5" ${canEdit ? "" : "disabled"}></textarea></div>
      <div class="field"><label>创作 / 生图人格补充</label><textarea id="bh-create" rows="5" ${canEdit ? "" : "disabled"}></textarea></div></div>
    <div class="panel"><h3>tt 路由与额外能力</h3><div class="grid2">
      <div class="field"><label>AstrBot 显式前缀（必须包含 tt）</label><input id="bh-prefix" ${canEdit ? "" : "disabled"}/></div>
      <div class="field"><label>联网结果数</label><input id="bh-search-max" type="number" min="1" max="10" ${canEdit ? "" : "disabled"}/></div></div>
      <label class="check-row"><input id="bh-compact" type="checkbox" ${canEdit ? "" : "disabled"}/> 支持 tt生图 / tt搜索（无空格）</label>
      <label class="check-row"><input id="bh-search" type="checkbox" ${canEdit ? "" : "disabled"}/> 启用联网搜索</label>
      <label class="check-row"><input id="bh-search-explicit" type="checkbox" ${canEdit ? "" : "disabled"}/> 仅明确要求“搜索 / 查一下 / 联网”时搜索</label>
      <label class="check-row"><input id="bh-search-prefix" type="checkbox" ${canEdit ? "" : "disabled"}/> 搜索必须由 tt 唤起（自动插话不联网）</label>
      <label class="check-row"><input id="bh-vision" type="checkbox" ${canEdit ? "" : "disabled"}/> 启用上下文识图</label></div>
    <div class="panel"><h3>群聊自动回复</h3><div class="field"><label>强度（统一由 Private Companion 负责）</label>
      <select id="bh-auto" ${canEdit ? "" : "disabled"}><option value="off">关闭</option><option value="occasional">偶尔</option><option value="frequent">经常</option></select></div>
      <p class="muted">偶尔：每天最多约 2 次主动插话、最短约 4 小时；经常：每天最多约 6 次、最短约 1 小时。AstrBot 自带随机回复会关闭，避免双回。</p></div>
    <div class="panel"><h3>生图安全改写与人设联动</h3>
      <label class="check-row"><input id="bh-rewrite" type="checkbox" ${canEdit ? "" : "disabled"}/> 生图前把提示词改成合法、易过审版本，同时保留主体和人设</label>
      <div class="field"><label>合法化规则（留空使用插件内置安全规则）</label><textarea id="bh-rewrite-prompt" rows="7" ${canEdit ? "" : "disabled"}></textarea></div>
      <p class="muted">提示词提到任意别名时，会解析对应稳定身份的人设、标签和任意属性，不再只读取生图发起者自己。</p></div>
    <div class="toolbar" id="bh-actions"></div></form>`);
  root.appendChild(form);
  const setValue = (id, value) => { form.querySelector(id).value = value || ""; };
  setValue("#bh-name", persona.bot_name); setValue("#bh-persona", persona.persona_id || persona.default_personality);
  setValue("#bh-style", persona.reply_style_prompt); setValue("#bh-talk", persona.conversation_prompt); setValue("#bh-create", persona.creative_prompt);
  setValue("#bh-prefix", (routing.astrbot_prefixes || ["tt"]).join(", ")); setValue("#bh-search-max", search.max_results || 5);
  form.querySelector("#bh-compact").checked = !!routing.compact_tt; form.querySelector("#bh-search").checked = !!search.enabled;
  form.querySelector("#bh-search-explicit").checked = !!search.only_explicit; form.querySelector("#bh-search-prefix").checked = !!search.require_astrbot_prefix;
  form.querySelector("#bh-vision").checked = !!image.vision_enabled; form.querySelector("#bh-auto").value = autoMode;
  form.querySelector("#bh-rewrite").checked = !!image.prompt_rewrite_enabled; setValue("#bh-rewrite-prompt", image.prompt_rewrite_system_prompt);
  if (!canEdit) return;
  form.querySelector("#bh-actions").appendChild(el(`<button type="submit">保存统一行为配置</button>`));
  form.onsubmit = async (event) => {
    event.preventDefault();
    const list = (value) => value.split(/[,，\s]+/).map((s) => s.trim()).filter(Boolean);
    const fields = {
      routing: { astrbot_prefixes: list(form.querySelector("#bh-prefix").value), compact_tt: form.querySelector("#bh-compact").checked },
      search: { enabled: form.querySelector("#bh-search").checked, only_explicit: form.querySelector("#bh-search-explicit").checked, require_astrbot_prefix: form.querySelector("#bh-search-prefix").checked, max_results: Number(form.querySelector("#bh-search-max").value) || 5 },
      persona: { bot_name: form.querySelector("#bh-name").value, persona_id: form.querySelector("#bh-persona").value, reply_style_prompt: form.querySelector("#bh-style").value, conversation_prompt: form.querySelector("#bh-talk").value, creative_prompt: form.querySelector("#bh-create").value },
      automatic_reply: { mode: form.querySelector("#bh-auto").value },
      image: { vision_enabled: form.querySelector("#bh-vision").checked, prompt_rewrite_enabled: form.querySelector("#bh-rewrite").checked, prompt_rewrite_system_prompt: form.querySelector("#bh-rewrite-prompt").value },
    };
    try {
      await api("/api/runtime-profile", { method: "PATCH", body: JSON.stringify({ fields }) });
      alert("已保存并自动备份。人格库即时生效；路由和插件配置请在运维页重启 AstrBot 后验收。");
      await renderBehavior(root);
    } catch (e) { alert(e.message); }
  };
}
/* ===================== 配置 / 资料 / 运维 ===================== */
async function renderConfig(root) {
  const canEdit = can("config.edit");
  root.innerHTML = "";
  const plugins = ["soulmap", "private_companion", "console_bridge", "cmd"];
  for (const plugin of plugins) {
    const panel = el(`<div class="panel"><h3>${plugin}</h3><div class="toolbar"></div><pre class="code"></pre></div>`);
    const pre = panel.querySelector("pre");
    const bar = panel.querySelector(".toolbar");
    const load = async (reveal = false) => {
      const data = await api(`/api/config/${plugin}?reveal=${reveal ? "true" : "false"}`);
      pre.textContent = JSON.stringify(data.config, null, 2);
      pre.dataset.raw = JSON.stringify(data.config, null, 2);
    };
    const refresh = el(`<button type="button" class="ghost">刷新（脱敏）</button>`);
    refresh.onclick = () => load(false);
    bar.appendChild(refresh);
    if (can("config.secrets")) {
      const reveal = el(`<button type="button" class="ghost">显示明文密钥</button>`);
      reveal.onclick = async () => {
        if (!confirm("确认显示明文？")) return;
        await load(true);
      };
      bar.appendChild(reveal);
    }
    if (canEdit && !(plugin === "cmd" && !can("config.secrets") && !can("console.manage"))) {
      const save = el(`<button type="button">从下方 JSON 保存</button>`);
      const ta = el(`<textarea class="doc-editor" style="min-height:220px;margin-top:8px"></textarea>`);
      save.onclick = async () => {
        try {
          const fields = JSON.parse(ta.value || pre.dataset.raw || "{}");
          await api(`/api/config/${plugin}`, {
            method: "PATCH",
            body: JSON.stringify({ fields }),
          });
          alert("已保存");
          await load(false);
        } catch (e) { alert(e.message); }
      };
      panel.appendChild(el(`<p class="muted">编辑时请只放要修改的字段 JSON 对象。</p>`));
      panel.appendChild(ta);
      bar.appendChild(save);
      load(false).then(() => { ta.value = "{}"; });
    } else {
      await load(false);
    }
    root.appendChild(panel);
  }
}

async function renderKnowledge(root) {
  const canEdit = can("knowledge.edit");
  root.innerHTML = "";
  const split = el(`<div class="split"><div class="panel"><h3>文档</h3><div class="doc-list" id="docs"></div></div>
    <div class="panel"><div class="toolbar"><strong id="doc-path"></strong></div>
    <textarea class="doc-editor" id="doc-body" ${canEdit ? "" : "readonly"}></textarea>
    <div class="toolbar" id="doc-actions"></div></div></div>`);
  root.appendChild(split);
  const list = split.querySelector("#docs");
  const pathEl = split.querySelector("#doc-path");
  const body = split.querySelector("#doc-body");
  const actions = split.querySelector("#doc-actions");

  const data = await api("/api/knowledge");
  async function openDoc(path) {
    state.knowledgePath = path;
    pathEl.textContent = path;
    const doc = await api(`/api/knowledge/${path}`);
    body.value = doc.content;
    list.querySelectorAll("button").forEach((b) => b.classList.toggle("active", b.dataset.path === path));
  }
  for (const d of data.docs) {
    const b = el(`<button type="button" data-path="${esc(d.path)}">${esc(d.path)}</button>`);
    b.onclick = () => openDoc(d.path);
    list.appendChild(b);
  }
  if (canEdit) {
    const save = el(`<button type="button">保存</button>`);
    save.onclick = async () => {
      if (!state.knowledgePath) return;
      await api(`/api/knowledge/${state.knowledgePath}`, {
        method: "PUT",
        body: JSON.stringify({ content: body.value }),
      });
      alert("已保存到服务器资料站");
    };
    const create = el(`<button type="button" class="ghost">新建文档</button>`);
    create.onclick = async () => {
      const name = prompt("文件名（如 notes/foo.md）");
      if (!name) return;
      await api(`/api/knowledge/${name}`, {
        method: "PUT",
        body: JSON.stringify({ content: `# ${name}\n\n` }),
      });
      await renderKnowledge(root);
    };
    actions.append(save, create);
  }
  if (data.docs.length) await openDoc(state.knowledgePath && data.docs.some((d) => d.path === state.knowledgePath) ? state.knowledgePath : data.docs[0].path);
}

async function renderOps(root) {
  root.innerHTML = "";
  const health = await api("/api/ops/health");
  root.appendChild(el(`<div class="panel"><h3>健康检查</h3><pre class="code">${esc(JSON.stringify(health, null, 2))}</pre></div>`));

  const logsPanel = el(`<div class="panel"><h3>AstrBot 日志</h3><div class="toolbar"></div><pre class="code" id="logs">…</pre></div>`);
  const refresh = el(`<button type="button" class="ghost">刷新日志</button>`);
  refresh.onclick = async () => {
    const data = await api("/api/ops/logs?tail=120");
    logsPanel.querySelector("#logs").textContent = data.logs || "";
  };
  logsPanel.querySelector(".toolbar").appendChild(refresh);
  root.appendChild(logsPanel);
  await refresh.onclick();

  const ops = el(`<div class="panel"><h3>操作</h3><div class="toolbar"></div></div>`);
  if (can("ops.restart")) {
    const restart = el(`<button type="button" class="danger">重启 AstrBot</button>`);
    restart.onclick = async () => {
      if (!confirm("确认 docker compose restart astrbot？")) return;
      try {
        const r = await api("/api/ops/restart-astrbot", { method: "POST" });
        alert("重启完成\n" + (r.output || ""));
      } catch (e) { alert(e.message); }
    };
    ops.querySelector(".toolbar").appendChild(restart);
  }
  if (can("knowledge.edit")) {
    const clog = el(`<button type="button" class="ghost">追加变更日志</button>`);
    clog.onclick = async () => {
      const summary = prompt("变更摘要");
      if (!summary) return;
      await api("/api/knowledge/changelog", {
        method: "POST",
        body: JSON.stringify({ summary, author: state.user.username }),
      });
      alert("已写入 40-change-log.md");
    };
    ops.querySelector(".toolbar").appendChild(clog);
  }
  if (ops.querySelector(".toolbar").children.length) root.appendChild(ops);
}

async function renderMessages(root) {
  if (!can("messages.view")) {
    root.innerHTML = '<p class="error">无消息中心查看权限。</p>';
    return;
  }

  const [targetsData, personasData, initialJobs] = await Promise.all([
    api("/api/messages/targets"),
    api("/api/messages/personas"),
    api("/api/messages/jobs?limit=100"),
  ]);
  const targets = targetsData.items || [];
  const personas = personasData.items || [];
  const previewTarget = {
    target_key: "preview:local",
    kind: "preview",
    display_name: "\u7f51\u9875\u9884\u89c8\uff08\u4e0d\u53d1\u9001\uff09",
    id_hint: "\u4ec5\u7f51\u9875",
  };
  let sourceDraftId = "";
  let confirmedTargetKey = "";

  root.innerHTML = "";
  const layout = el(`
    <div class="split wide message-layout">
      <section>
        <div class="panel message-compose">
          <h3>按 Bot 人格生成草稿</h3>
          <p class="muted">目标只能来自 Private Companion 已知且当前允许的会话。这里先生成草稿，不会直接发送。</p>
          <div class="field"><label>目标会话</label><select id="message-target"></select></div>
          <div class="field"><label>草稿要求</label><textarea id="message-prompt" maxlength="4000" placeholder="例如：按照主 Bot 人格和已有人设，礼貌提醒大家今晚维护；只写可发送正文。"></textarea></div>
          <div class="toolbar"><button type="button" id="message-draft">生成人格草稿</button><span id="message-compose-note" class="muted"></span></div>
        </div>
        <div id="message-review-slot"></div>
      </section>
      <section class="panel">
        <div class="backup-head"><div><h3>草稿与投递任务</h3><p class="muted small">投递中断会标记为 uncertain，系统不会自动重发。</p></div><button type="button" class="ghost" id="message-refresh">刷新</button></div>
        <div id="message-jobs"></div>
      </section>
    </div>
  `);
  root.appendChild(layout);

  const targetSelect = layout.querySelector("#message-target");
  const compose = layout.querySelector(".message-compose");
  compose.querySelector("h3").textContent = "\u6309\u9009\u5b9a\u4eba\u683c\u751f\u6210\u56de\u7b54\u6216\u8349\u7a3f";
  compose.querySelector("p.muted").textContent = "\u53ef\u5148\u5728\u7f51\u9875\u9884\u89c8\u4eba\u683c\u56de\u7b54\uff1b\u53ea\u6709\u5df2\u77e5\u4e14\u5f53\u524d\u5141\u8bb8\u7684\u4f1a\u8bdd\u624d\u80fd\u8fdb\u5165\u771f\u5b9e\u6295\u9012\u786e\u8ba4\u3002";
  compose.querySelector("#message-prompt").parentElement.querySelector("label").textContent = "\u95ee\u9898 / \u8349\u7a3f\u8981\u6c42";
  const personaField = el('<div class="field"><label>\u56de\u7b54\u4eba\u683c</label><select id="message-persona"></select></div>');
  targetSelect.parentElement.insertAdjacentElement("afterend", personaField);
  const personaSelect = personaField.querySelector("#message-persona");
  const previewOption = document.createElement("option");
  previewOption.value = previewTarget.target_key;
  previewOption.textContent = `\u7f51\u9875\u9884\u89c8 \u00b7 ${previewTarget.display_name} \u00b7 ${previewTarget.id_hint}`;
  targetSelect.appendChild(previewOption);
  if (!targets.length) {
    const option = document.createElement("option");
    option.value = "";
    option.textContent = "暂无已知且允许的会话";
    targetSelect.appendChild(option);
  } else {
    targets.forEach((target) => {
      const option = document.createElement("option");
      option.value = target.target_key;
      const kind = target.kind === "group" ? "群聊" : "私聊";
      option.textContent = `${kind} · ${target.display_name || "未命名"} · ${target.id_hint || "未知"}`;
      targetSelect.appendChild(option);
    });
  }

  personas.forEach((persona) => {
    const option = document.createElement("option");
    option.value = persona.persona_key;
    const tags = Array.isArray(persona.tags) && persona.tags.length ? ` \u00b7 ${persona.tags.join(" / ")}` : "";
    option.textContent = `${persona.display_name || "\u4e3b Bot \u4eba\u683c"}${tags}`;
    personaSelect.appendChild(option);
  });
  if (!personas.length) {
    const option = document.createElement("option");
    option.value = "bot:default";
    option.textContent = "\u4e3b Bot \u4eba\u683c";
    personaSelect.appendChild(option);
  }

  const note = layout.querySelector("#message-compose-note");
  const draftButton = layout.querySelector("#message-draft");
  if (!can("messages.compose")) {
    draftButton.classList.add("hidden");
    layout.querySelector("#message-prompt").disabled = true;
    personaSelect.disabled = true;
    note.textContent = "当前账号只有查看权限。";
  }

  const clearConfirmation = () => {
    confirmedTargetKey = "";
    const box = layout.querySelector("#message-confirm-box");
    if (box) box.classList.add("hidden");
    const phrase = layout.querySelector("#message-confirm-phrase");
    if (phrase) phrase.value = "";
  };
  targetSelect.addEventListener("change", clearConfirmation);

  if (can("messages.send")) {
    const review = el(`
      <div class="panel message-review">
        <h3>人工审核与确认投递</h3>
        <p class="muted">请逐字检查正文。选择目标或编辑正文后，都必须重新确认目标并输入固定短语 <span class="mono">SEND</span>。</p>
        <div class="field"><label>待发送正文（最多 2000 字）</label><textarea id="message-body" maxlength="2000" placeholder="从右侧载入草稿，或在此人工填写。"></textarea></div>
        <p class="muted small" id="message-source">未载入草稿</p>
        <button type="button" class="danger" id="message-open-confirm">锁定目标并二次确认</button>
        <div id="message-confirm-box" class="message-confirm hidden">
          <strong>最后确认</strong>
          <p id="message-confirm-target"></p>
          <label class="field">输入 SEND<input id="message-confirm-phrase" autocomplete="off" spellcheck="false" /></label>
          <div class="toolbar"><button type="button" class="danger" id="message-commit-send" disabled>提交 SEND 确认</button><button type="button" class="ghost" id="message-cancel-confirm">取消</button></div>
          <p class="error" id="message-send-error"></p>
        </div>
      </div>
    `);
    layout.querySelector("#message-review-slot").appendChild(review);
    const body = review.querySelector("#message-body");
    body.addEventListener("input", clearConfirmation);
    review.querySelector("#message-open-confirm").onclick = () => {
      const targetKey = targetSelect.value;
      const message = body.value.trim();
      if (!targetKey) return alert("请先选择一个当前允许的目标会话。");
      if (!message) return alert("待发送正文不能为空。");
      if (targetKey === previewTarget.target_key) {
        alert("\u7f51\u9875\u9884\u89c8\u4e0d\u80fd\u53d1\u9001\u3002\u8bf7\u5148\u9009\u62e9\u4e00\u4e2a\u5f53\u524d\u5141\u8bb8\u7684\u79c1\u804a\u6216\u7fa4\u804a\u76ee\u6807\u3002");
        return;
      }
      const target = targets.find((item) => item.target_key === targetKey);
      if (!target) return alert("目标已不在当前列表，请刷新后重试。");
      confirmedTargetKey = targetKey;
      review.querySelector("#message-confirm-target").textContent = `将向 ${target.kind === "group" ? "群聊" : "私聊"}“${target.display_name}”（${target.id_hint}）提交确认投递任务。`;
      review.querySelector("#message-confirm-box").classList.remove("hidden");
      const phrase = review.querySelector("#message-confirm-phrase");
      phrase.value = "";
      review.querySelector("#message-commit-send").disabled = true;
      phrase.focus();
    };
    review.querySelector("#message-cancel-confirm").onclick = clearConfirmation;
    review.querySelector("#message-confirm-phrase").addEventListener("input", (event) => {
      review.querySelector("#message-commit-send").disabled = event.target.value !== "SEND";
    });
    review.querySelector("#message-commit-send").onclick = async () => {
      const error = review.querySelector("#message-send-error");
      error.textContent = "";
      const targetKey = targetSelect.value;
      const phrase = review.querySelector("#message-confirm-phrase").value;
      if (!confirmedTargetKey || confirmedTargetKey !== targetKey || phrase !== "SEND") {
        error.textContent = "目标或 SEND 确认已失效，请重新锁定目标。";
        return;
      }
      const button = review.querySelector("#message-commit-send");
      button.disabled = true;
      try {
        await api("/api/messages/send", {
          method: "POST",
          body: JSON.stringify({
            target_key: targetKey,
            message: body.value.trim(),
            source_draft_id: sourceDraftId,
            confirm_target: confirmedTargetKey,
            confirm_phrase: phrase,
          }),
        });
        body.value = "";
        sourceDraftId = "";
        review.querySelector("#message-source").textContent = "投递任务已排队；请在右侧确认最终状态。";
        clearConfirmation();
        await reloadJobs();
      } catch (err) {
        error.textContent = err.message;
        button.disabled = phrase !== "SEND";
      }
    };
  }

  const jobsRoot = layout.querySelector("#message-jobs");
  async function reloadJobs(prefetched = null) {
    if (!jobsRoot.isConnected || state.view !== "messages") return;
    if (state.messagePollTimer) {
      clearTimeout(state.messagePollTimer);
      state.messagePollTimer = null;
    }
    const data = prefetched || await api("/api/messages/jobs?limit=100");
    const jobs = data.items || [];
    jobsRoot.innerHTML = "";
    if (!jobs.length) {
      jobsRoot.innerHTML = '<p class="muted">暂无消息任务。</p>';
    }
    jobs.forEach((job) => {
      const status = String(job.status || "");
      const statusClass = ["sent", "draft_ready"].includes(status) ? "on" : (["failed", "uncertain"].includes(status) ? "off" : "");
      const typeLabel = job.type === "send" ? "投递" : "草稿";
      const preview = job.draft || job.message || job.prompt || "";
      const card = el(`
        <article class="mem-card message-job ${status === "uncertain" ? "message-uncertain" : ""}">
          <div class="backup-head"><div><strong>${esc(typeLabel)} · ${esc(job.target_display_name || "未知目标")}</strong><div class="muted small">${esc(job.target_kind === "group" ? "群聊" : "私聊")} · ${esc(job.target_id_hint || "未知")} · ${esc(fmtMessageTime(job.created_at))}</div></div><span class="badge ${statusClass}">${esc(messageStatusLabel(status))}</span></div>
          ${preview ? `<pre class="message-preview">${esc(preview)}</pre>` : ""}
          ${job.error ? `<p class="error">${esc(job.error)}</p>` : ""}
          <div class="toolbar message-job-actions"></div>
        </article>
      `);
      const actions = card.querySelector(".message-job-actions");
      const jobTypeLabel = job.type === "send"
        ? "\u6295\u9012"
        : (job.target_kind === "preview" ? "\u7f51\u9875\u56de\u7b54" : "\u8349\u7a3f");
      const jobTargetKindLabel = job.target_kind === "preview"
        ? "\u7f51\u9875\u9884\u89c8"
        : (job.target_kind === "group" ? "\u7fa4\u804a" : "\u79c1\u804a");
      const jobHeading = card.querySelector(".backup-head strong");
      if (jobHeading) jobHeading.textContent = `${jobTypeLabel} \u00b7 ${job.target_display_name || "\u672a\u77e5\u76ee\u6807"}`;
      const jobMeta = card.querySelector(".backup-head .muted.small");
      if (jobMeta) {
        const personaText = job.persona_display_name ? ` \u00b7 ${job.persona_display_name}` : "";
        jobMeta.textContent = `${jobTargetKindLabel} \u00b7 ${job.target_id_hint || "\u672a\u77e5"}${personaText} \u00b7 ${fmtMessageTime(job.created_at)}`;
      }
      if (status === "draft_ready" && job.draft && can("messages.send")) {
        const load = el('<button type="button" class="ghost">载入审核区</button>');
        if (job.target_kind === "preview") load.textContent = "\u8f7d\u5165\u5ba1\u6838\u533a\uff08\u9700\u9009\u76ee\u6807\uff09";
        load.onclick = () => {
          if (![...targetSelect.options].some((option) => option.value === job.target_key)) {
            alert("该草稿目标已不在当前允许列表，不能载入发送。");
            return;
          }
          targetSelect.value = job.target_key;
          if (job.persona_key && [...personaSelect.options].some((option) => option.value === job.persona_key)) {
            personaSelect.value = job.persona_key;
          }
          layout.querySelector("#message-body").value = job.draft;
          sourceDraftId = String(job.id || "");
          layout.querySelector("#message-source").textContent = `已载入草稿任务 ${String(job.id || "").slice(0, 12)}；发送前仍需人工审核。`;
          clearConfirmation();
          layout.querySelector("#message-body").focus();
        };
        actions.appendChild(load);
      }
      if (["pending", "draft_ready"].includes(status) && can("messages.send")) {
        const cancel = el('<button type="button" class="ghost">取消任务</button>');
        cancel.onclick = async () => {
          if (!confirm("确认取消这个尚未发送的任务？")) return;
          await api(`/api/messages/jobs/${encodeURIComponent(job.id)}`, { method: "DELETE" });
          await reloadJobs();
        };
        actions.appendChild(cancel);
      }
      jobsRoot.appendChild(card);
    });
    if (jobs.some((job) => ["pending", "processing", "sending"].includes(job.status)) && state.view === "messages") {
      state.messagePollTimer = setTimeout(() => reloadJobs().catch((err) => {
        jobsRoot.innerHTML = `<p class="error">${esc(err.message)}</p>`;
      }), 1500);
    }
  }

  layout.querySelector("#message-refresh").onclick = () => reloadJobs().catch((err) => alert(err.message));
  draftButton.onclick = async () => {
    const targetKey = targetSelect.value;
    const personaKey = personaSelect.value;
    const prompt = layout.querySelector("#message-prompt").value.trim();
    if (!targetKey) return alert("当前没有可用目标会话。");
    if (!prompt) return alert("请填写草稿要求。");
    draftButton.disabled = true;
    note.textContent = "草稿任务正在排队…";
    try {
      await api("/api/messages/draft", {
        method: "POST",
        body: JSON.stringify({ target_key: targetKey, persona_key: personaKey, prompt }),
      });
      note.textContent = "已排队；右侧会自动刷新生成状态。";
      await reloadJobs();
    } catch (err) {
      note.textContent = err.message;
    } finally {
      draftButton.disabled = false;
    }
  };

  await reloadJobs(initialJobs);
}
async function renderAuditBackups(root) {
  root.innerHTML = "";
  let reloadAudit = async () => {};

  if (can("audit.view")) {
    const panel = el('<div class="panel"><h3>写操作审计</h3><p class="muted">只记录操作者、动作、资源路径与结果；不保存请求正文、密码、Key 或 Token。</p><div class="toolbar"><input id="audit-q" placeholder="搜索账号 / 动作 / 路径" /><select id="audit-outcome"><option value="">全部结果</option><option value="success">成功</option><option value="rejected">未接受</option><option value="denied">拒绝</option><option value="error">错误</option></select><button type="button" class="ghost" id="audit-refresh">刷新</button><span class="muted" id="audit-total"></span></div><div class="table-wrap"><table><thead><tr><th>时间</th><th>账号</th><th>动作</th><th>资源</th><th>结果</th></tr></thead><tbody id="audit-rows"></tbody></table></div></div>');
    root.appendChild(panel);
    const q = panel.querySelector("#audit-q");
    const outcome = panel.querySelector("#audit-outcome");
    reloadAudit = async () => {
      const params = new URLSearchParams({ limit: "200" });
      if (q.value.trim()) params.set("q", q.value.trim());
      if (outcome.value) params.set("outcome", outcome.value);
      const data = await api("/api/audit?" + params.toString());
      panel.querySelector("#audit-total").textContent = "共 " + data.total + " 条，显示最近 " + data.items.length + " 条";
      const tbody = panel.querySelector("#audit-rows");
      tbody.innerHTML = "";
      for (const item of data.items) {
        const badgeClass = item.outcome === "success" ? "on" : (item.outcome === "error" || item.outcome === "denied" ? "off" : "");
        tbody.appendChild(el(
          '<tr><td>' + esc(fmtTime(item.created_at)) + '</td>' +
          '<td><strong>' + esc(item.actor_username) + '</strong><div class="muted small">' + esc(item.actor_role || "—") + '</div></td>' +
          '<td><span class="mono small">' + esc(item.action) + '</span><div class="muted small">' + esc(item.method) + '</div></td>' +
          '<td class="mono small">' + esc(item.resource) + '</td>' +
          '<td><span class="badge ' + badgeClass + '">' + esc(outcomeLabel(item.outcome)) + '</span><div class="muted small">HTTP ' + esc(item.status_code) + '</div></td></tr>'
        ));
      }
      if (!data.items.length) {
        tbody.innerHTML = '<tr><td colspan="5" class="muted">暂无匹配审计记录</td></tr>';
      }
    };
    panel.querySelector("#audit-refresh").onclick = reloadAudit;
    q.oninput = debounce(() => reloadAudit().catch((e) => alert(e.message)), 300);
    outcome.onchange = () => reloadAudit().catch((e) => alert(e.message));
    await reloadAudit();
  }

  if (can("backups.view")) {
    const panel = el('<div class="panel"><h3>JSON 写前备份</h3><p class="muted">回滚只接受快照内通过 JSON 校验的文件；写回前会先备份当前线上文件。回滚后通常需要重启 AstrBot 才能完整生效。</p><div class="toolbar"><button type="button" class="ghost" id="backup-refresh">刷新备份</button><span class="muted" id="backup-total"></span></div><div id="backup-list"></div></div>');
    root.appendChild(panel);

    async function restore(snapshot, files) {
      const scope = files ? files.join(", ") : "快照内全部 JSON";
      const typed = prompt("将回滚 " + scope + "。请输入快照名确认：\n" + snapshot, "");
      if (typed !== snapshot) {
        if (typed !== null) alert("快照名不匹配，未执行回滚");
        return;
      }
      const result = await api("/api/backups/restore", {
        method: "POST",
        body: JSON.stringify({ snapshot, files, confirm: typed }),
      });
      alert("已回滚 " + result.restored.length + " 个文件。\n当前文件的安全备份：" + (result.safety_backup || "无") + "\n请在确认内容后重启 AstrBot。");
      await reloadBackups();
      await reloadAudit();
    }

    async function reloadBackups() {
      const data = await api("/api/backups?limit=100");
      panel.querySelector("#backup-total").textContent = "共 " + data.total + " 个快照";
      const list = panel.querySelector("#backup-list");
      list.innerHTML = "";
      for (const snapshot of data.items) {
        const card = el('<div class="mem-card"><div class="backup-head"><div><strong class="mono">' + esc(snapshot.name) + '</strong><div class="muted small">' + esc(fmtTime(snapshot.created_at)) + ' · ' + snapshot.file_count + ' 个 JSON · ' + esc(fmtBytes(snapshot.total_bytes)) + '</div></div><div class="toolbar backup-actions"></div></div></div>');
        const actions = card.querySelector(".backup-actions");
        if (can("backups.restore") && snapshot.file_count > 0) {
          const all = el('<button type="button" class="danger">回滚整个快照</button>');
          all.onclick = () => restore(snapshot.name, null).catch((e) => alert(e.message));
          actions.appendChild(all);
        }
        const details = document.createElement("details");
        const summary = document.createElement("summary");
        summary.textContent = "查看文件";
        details.appendChild(summary);
        for (const file of snapshot.files || []) {
          const row = el('<div class="backup-file"><span class="mono small">' + esc(file.path) + '</span><span class="muted small">' + esc(fmtBytes(file.bytes)) + '</span><span></span></div>');
          if (can("backups.restore")) {
            const one = el('<button type="button" class="ghost">仅回滚此文件</button>');
            one.onclick = () => restore(snapshot.name, [file.path]).catch((e) => alert(e.message));
            row.lastElementChild.appendChild(one);
          }
          details.appendChild(row);
        }
        if (snapshot.files_truncated) {
          details.appendChild(el('<p class="muted small">文件列表过长，页面仅显示前 500 项。</p>'));
        }
        card.appendChild(details);
        list.appendChild(card);
      }
      if (!data.items.length) list.innerHTML = '<p class="muted">暂无管理台写前备份。</p>';
    }

    panel.querySelector("#backup-refresh").onclick = () => reloadBackups().catch((e) => alert(e.message));
    await reloadBackups();
  }
}

/* ===================== 我的账号 / 改密码 ===================== */
async function renderAccount(root) {
  root.innerHTML = "";
  root.appendChild(el(`
    <div class="panel">
      <h3>当前账号</h3>
      <p>用户名：<strong>${esc(state.user.username)}</strong></p>
      <p>权限组：<strong>${esc(state.user.role)}</strong></p>
      <p class="muted">有效权限：${esc((state.user.permissions || []).join(", "))}</p>
    </div>
  `));

  if (!can("account.password")) {
    root.appendChild(el(`<p class="error">无改密权限</p>`));
    return;
  }

  const form = el(`<form class="panel account-form">
    <h3>修改密码</h3>
    <label>当前密码<input type="password" id="pw-cur" required autocomplete="current-password" /></label>
    <label>新密码<input type="password" id="pw-new" required minlength="6" autocomplete="new-password" /></label>
    <label>确认新密码<input type="password" id="pw-new2" required minlength="6" autocomplete="new-password" /></label>
    <button type="submit">保存新密码</button>
    <p class="error" id="pw-err"></p>
    <p class="ok hidden" id="pw-ok">密码已更新</p>
  </form>`);
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const err = form.querySelector("#pw-err");
    const ok = form.querySelector("#pw-ok");
    err.textContent = "";
    ok.classList.add("hidden");
    const cur = form.querySelector("#pw-cur").value;
    const n1 = form.querySelector("#pw-new").value;
    const n2 = form.querySelector("#pw-new2").value;
    if (n1 !== n2) {
      err.textContent = "两次新密码不一致";
      return;
    }
    try {
      await api("/api/auth/change-password", {
        method: "POST",
        body: JSON.stringify({ current_password: cur, new_password: n1 }),
      });
      ok.classList.remove("hidden");
      form.reset();
    } catch (ex) {
      err.textContent = ex.message;
    }
  });
  root.appendChild(form);
}

/* ===================== 账号与权限 ===================== */
async function renderConsoleUsers(root) {
  if (!can("console.manage")) {
    root.innerHTML = `<p class="error">需要 console.manage</p>`;
    return;
  }
  const rolesData = await api("/api/console-users/roles");
  const users = await api("/api/console-users");
  const catalog = rolesData.permissions || {};
  const roles = rolesData.roles || [];

  root.innerHTML = "";

  // Roles panel
  const rolePanel = el(`<div class="panel"><h3>权限组</h3><div class="toolbar" id="role-bar"></div><div id="role-list"></div></div>`);
  const createRoleBtn = el(`<button type="button">+ 自定义权限组</button>`);
  createRoleBtn.onclick = async () => {
    const name = prompt("权限组英文名（如 memory_ops）");
    if (!name) return;
    const label = prompt("显示名", name) || name;
    try {
      await api("/api/console-users/roles", {
        method: "POST",
        body: JSON.stringify({ name, label, permissions: ["account.password", "memories.view"] }),
      });
      await renderConsoleUsers(root);
    } catch (e) { alert(e.message); }
  };
  rolePanel.querySelector("#role-bar").appendChild(createRoleBtn);

  const roleList = rolePanel.querySelector("#role-list");
  for (const role of roles) {
    const box = el(`<div class="role-card">
      <div class="toolbar">
        <strong>${esc(role.label)} <span class="muted mono">(${esc(role.name)})</span></strong>
        ${role.is_system ? '<span class="badge">系统</span>' : ""}
      </div>
      <div class="perm-grid" data-role="${esc(role.name)}"></div>
      <div class="toolbar role-actions"></div>
    </div>`);
    const grid = box.querySelector(".perm-grid");
    const selected = new Set(role.permissions || []);
    for (const [perm, desc] of Object.entries(catalog)) {
      const id = `r-${role.name}-${perm}`;
      const lab = el(`<label class="perm-item" for="${id}">
        <input type="checkbox" id="${id}" data-perm="${esc(perm)}" ${selected.has(perm) ? "checked" : ""} />
        <span><code>${esc(perm)}</code> ${esc(desc)}</span>
      </label>`);
      grid.appendChild(lab);
    }
    const actions = box.querySelector(".role-actions");
    const save = el(`<button type="button">保存权限组</button>`);
    save.onclick = async () => {
      const permissions = [...grid.querySelectorAll("input:checked")].map((i) => i.dataset.perm);
      try {
        await api(`/api/console-users/roles/${encodeURIComponent(role.name)}`, {
          method: "PATCH",
          body: JSON.stringify({ permissions }),
        });
        alert("权限组已保存");
      } catch (e) { alert(e.message); }
    };
    actions.appendChild(save);
    if (!role.is_system) {
      const del = el(`<button type="button" class="ghost danger">删除组</button>`);
      del.onclick = async () => {
        if (!confirm(`删除权限组 ${role.name}？`)) return;
        try {
          await api(`/api/console-users/roles/${encodeURIComponent(role.name)}`, { method: "DELETE" });
          await renderConsoleUsers(root);
        } catch (e) { alert(e.message); }
      };
      actions.appendChild(del);
    }
    roleList.appendChild(box);
  }
  root.appendChild(rolePanel);

  // Users panel
  const userPanel = el(`<div class="panel"><h3>控制台用户</h3><div class="toolbar" id="user-bar"></div></div>`);
  const add = el(`<button type="button">创建用户</button>`);
  add.onclick = async () => {
    const username = prompt("用户名");
    if (!username) return;
    const password = prompt("密码（至少 6 位）");
    if (!password) return;
    const role = prompt(`角色（${roles.map((r) => r.name).join(" / ")}）`, "editor");
    try {
      await api("/api/console-users", {
        method: "POST",
        body: JSON.stringify({ username, password, role }),
      });
      await renderConsoleUsers(root);
    } catch (e) { alert(e.message); }
  };
  const tokenBtn = el(`<button type="button" class="ghost">生成 API Token</button>`);
  tokenBtn.onclick = async () => {
    const label = prompt("标签", "local-sync") || "local-sync";
    const r = await api("/api/auth/token", { method: "POST", body: JSON.stringify({ label }) });
    prompt("请立刻保存此 token（只显示一次）", r.token);
  };
  userPanel.querySelector("#user-bar").append(add, tokenBtn);

  const table = el(`<div class="table-wrap"><table>
    <thead><tr><th>ID</th><th>用户名</th><th>权限组</th><th>额外授权</th><th>单独禁用</th><th>操作</th></tr></thead><tbody></tbody>
  </table></div>`);
  const tbody = table.querySelector("tbody");
  for (const u of users) {
    const tr = el(`<tr>
      <td>${u.id}</td>
      <td>${esc(u.username)}</td>
      <td class="role-cell"></td>
      <td class="grant-cell muted small"></td>
      <td class="deny-cell muted small"></td>
      <td class="ops-cell"></td>
    </tr>`);
    const roleSel = el(`<select></select>`);
    for (const r of roles) {
      const opt = el(`<option value="${esc(r.name)}">${esc(r.label)} (${esc(r.name)})</option>`);
      if (r.name === u.role) opt.selected = true;
      roleSel.appendChild(opt);
    }
    tr.querySelector(".role-cell").appendChild(roleSel);
    tr.querySelector(".grant-cell").textContent = (u.grants || []).join(", ") || "—";
    tr.querySelector(".deny-cell").textContent = (u.denies || []).join(", ") || "—";

    const cell = tr.querySelector(".ops-cell");
    const save = el(`<button type="button" class="ghost">保存组</button>`);
    save.onclick = async () => {
      try {
        await api(`/api/console-users/${u.id}`, {
          method: "PATCH",
          body: JSON.stringify({ role: roleSel.value }),
        });
        alert("已更新权限组");
      } catch (e) { alert(e.message); }
    };
    const overrides = el(`<button type="button" class="ghost">单独权限</button>`);
    overrides.onclick = async () => {
      const grants = prompt("额外授权（逗号分隔权限码）", (u.grants || []).join(","));
      if (grants == null) return;
      const denies = prompt("单独禁用（逗号分隔权限码）", (u.denies || []).join(","));
      if (denies == null) return;
      try {
        await api(`/api/console-users/${u.id}`, {
          method: "PATCH",
          body: JSON.stringify({
            grants: grants.split(",").map((s) => s.trim()).filter(Boolean),
            denies: denies.split(",").map((s) => s.trim()).filter(Boolean),
          }),
        });
        await renderConsoleUsers(root);
      } catch (e) { alert(e.message); }
    };
    const resetPw = el(`<button type="button" class="ghost">重置密码</button>`);
    resetPw.onclick = async () => {
      const password = prompt(`为 ${u.username} 设置新密码（至少 6 位）`);
      if (!password) return;
      try {
        await api(`/api/console-users/${u.id}`, {
          method: "PATCH",
          body: JSON.stringify({ password }),
        });
        alert("密码已重置");
      } catch (e) { alert(e.message); }
    };
    const del = el(`<button type="button" class="ghost danger">删除</button>`);
    del.onclick = async () => {
      if (!confirm(`删除 ${u.username}?`)) return;
      try {
        await api(`/api/console-users/${u.id}`, { method: "DELETE" });
        await renderConsoleUsers(root);
      } catch (e) { alert(e.message); }
    };
    cell.append(save, overrides, resetPw, del);
    tbody.appendChild(tr);
  }
  userPanel.appendChild(table);
  root.appendChild(userPanel);
}

boot();
