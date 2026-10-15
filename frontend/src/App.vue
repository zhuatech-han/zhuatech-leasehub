<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import { request } from "./api.js";
import {
  states,
  labels,
  columns,
  titles,
  hints,
  permissionLabels,
  cents,
  money,
} from "./model.js";
const profile = ref(null),
  login = ref({ username: "", password: "" }),
  error = ref(""),
  notice = ref(""),
  busy = ref(false),
  view = ref("overview"),
  resource = ref("assets"),
  lookup = ref({}),
  report = ref(null),
  data = ref({ items: [], total: 0 }),
  page = ref(1),
  search = ref(""),
  state = ref(""),
  adminTab = ref("organizations"),
  portalTab = ref("contracts"),
  modal = ref(null),
  form = ref({}),
  detail = ref(null),
  about = ref(false);
const account = computed(() => profile.value?.account || {}),
  hq = computed(() => account.value.scope === "HQ"),
  client = computed(() => account.value.scope === "CUSTOMER"),
  perms = computed(() => profile.value?.permissions || []),
  menu = computed(() => profile.value?.menus || []),
  heading = computed(() => titles[view.value]),
  canCreate = computed(
    () =>
      !client.value &&
      !["bills", "settings", "menus", "audit", "transfers"].includes(
        resource.value,
      ) &&
      !(resource.value === "assets" && !hq.value) &&
      !(resource.value === "rates" && !hq.value),
  ),
  canExport = computed(
    () =>
      perms.value.includes("reports") &&
      [
        "assets",
        "contracts",
        "bills",
        "receipts",
        "costs",
        "settlements",
      ].includes(resource.value),
  );
const today = () =>
  new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Shanghai" }).format(
    new Date(),
  );
function pretty(key, value) {
  if (key === "permissions")
    return String(value)
      .split(",")
      .map((p) => permissionLabels[p] || p)
      .join(" · ");
  if (key === "permission") return permissionLabels[value] || value;
  if (key === "kind")
    return (
      { asset_category: "设备分类", cost_category: "费用分类" }[value] || value
    );
  if (value == null) return "—";
  if (
    key.endsWith("_cents") ||
    ["billed", "collected", "overdue"].includes(key)
  )
    return money(value);
  if (key === "hq_bps") return Number(value) / 100 + "%";
  if (["enabled", "active"].includes(key)) return value ? "启用" : "停用";
  const map = {
    org_id: "organizations",
    customer_id: "customers",
    asset_id: "assets",
    rate_id: "rates",
    role_id: "roles",
    owner_id: "accounts",
    actor_id: "accounts",
    creator_id: "accounts",
    reviewer_id: "accounts",
    contract_id: "contracts",
    bill_id: "bills",
  };
  if (map[key]) {
    const r = lookup.value[map[key]]?.find(
      (x) => Number(x.id) === Number(value),
    );
    return r?.name || r?.display_name || r?.serial || "#" + value;
  }
  if (key === "category")
    return (
      lookup.value.dictionary?.find((x) => x.code === value)?.label || value
    );
  return states[value] || value;
}
async function run(task) {
  error.value = "";
  notice.value = "";
  busy.value = true;
  try {
    await task();
  } catch (e) {
    error.value = e.message;
    if (e.status === 401) profile.value = null;
  } finally {
    busy.value = false;
  }
}
async function load() {
  lookup.value = await request("/lookup");
  if (["overview", "reports"].includes(view.value))
    report.value = await request("/reports");
  else
    data.value = await request(
      `/data/${resource.value}?page=${page.value}&search=${encodeURIComponent(search.value)}&state=${encodeURIComponent(state.value)}`,
    );
}
async function navigate(code) {
  view.value = code;
  page.value = 1;
  search.value = "";
  state.value = "";
  detail.value = null;
  if (code === "admin") resource.value = adminTab.value;
  else if (code === "portal") resource.value = portalTab.value;
  else resource.value = code;
  await run(load);
}
async function signIn() {
  await run(async () => {
    profile.value = await request("/auth/login", "POST", login.value);
    login.value.password = "";
    await navigate(client.value ? "portal" : menu.value[0]?.code || "assets");
  });
}
async function signOut() {
  await run(async () => {
    await request("/auth/logout", "POST");
    profile.value = null;
  });
}
onMounted(() =>
  run(async () => {
    try {
      profile.value = await request("/auth/me");
      await navigate(client.value ? "portal" : menu.value[0]?.code || "assets");
    } catch (e) {
      if (e.status !== 401) throw e;
    }
  }),
);
function options(key) {
  const map = {
    org_id: "organizations",
    customer_id: "customers",
    asset_id: "assets",
    rate_id: "rates",
    role_id: "roles",
    owner_id: "accounts",
    bill_id: "bills",
  };
  let rows = lookup.value[map[key]] || [];
  if (
    ["customer_id", "asset_id", "rate_id", "bill_id"].includes(key) &&
    form.value.org_id
  )
    rows = rows.filter((x) => Number(x.org_id) === Number(form.value.org_id));
  if (key === "asset_id" && modal.value?.resource === "contracts")
    rows = rows.filter((x) => x.state === "PARTNER_AVAILABLE");
  return rows.map((x) => ({
    value: x.id,
    label:
      x.name ||
      x.display_name ||
      (key === "bill_id"
        ? `账单 #${x.id} · 未收 ${money(Number(x.amount_cents) - Number(x.paid_cents))}`
        : `#${x.id}`),
  }));
}
function fields() {
  const r = modal.value?.resource,
    a = modal.value?.action;
  if (a) {
    return [
      "note",
      ...(a === "allocate" ? ["org_id"] : []),
      ...(a === "return" ? ["return_on"] : []),
      ...(a === "deliver" ? ["delivered_on"] : []),
      ...(a === "follow" ? ["next_on"] : []),
      ...(["pay", "unpay"].includes(a) ? ["transferred_on", "reference"] : []),
      ...(a === "reset-password" ? ["password"] : []),
    ];
  }
  if (modal.value?.edit)
    return (
      {
        organizations: ["name", "enabled"],
        accounts: ["display_name", "role_id", "enabled"],
        roles: ["name", "permissions"],
        dictionary: ["label", "enabled"],
        menus: ["label", "position", "enabled"],
        settings: ["value"],
        rates: ["enabled"],
        customers: ["name", "contact"],
      }[r] || []
    );
  const org = hq.value ? ["org_id"] : [];
  return (
    {
      organizations: ["name", "code"],
      accounts: [
        "username",
        "display_name",
        "password",
        "role_id",
        ...(lookup.value.roles?.find(
          (x) => Number(x.id) === Number(form.value.role_id),
        )?.scope === "HQ"
          ? []
          : ["org_id"]),
        ...(lookup.value.roles?.find(
          (x) => Number(x.id) === Number(form.value.role_id),
        )?.scope === "CUSTOMER"
          ? ["customer_id"]
          : []),
      ],
      roles: ["name", "scope", "permissions"],
      dictionary: ["kind", "code", "label"],
      assets: ["serial", "name", "category", "purchase_cents"],
      customers: [...org, "name", "contact"],
      rates: [...org, "name", "daily_cents", "hq_bps", "effective_on"],
      contracts: [
        ...org,
        "name",
        "customer_id",
        "asset_id",
        "rate_id",
        "start_on",
        "end_on",
      ],
      receipts: [
        ...org,
        "bill_id",
        "amount_cents",
        "received_on",
        "reference",
        "note",
      ],
      costs: [
        ...org,
        "asset_id",
        "category",
        "amount_cents",
        "incurred_on",
        "reference",
        "note",
      ],
      settlements: [...org, "month_on"],
      risks: [...org, "title", "asset_id", "owner_id", "next_on"],
    }[r] || []
  );
}
const availablePermissions = computed(() =>
  form.value.scope === "CUSTOMER"
    ? ["portal"]
    : form.value.scope === "PARTNER"
      ? ["assets", "contracts", "finance", "risk", "reports"]
      : [
          "admin",
          "assets",
          "contracts",
          "finance",
          "approve",
          "risk",
          "reports",
        ],
);
function togglePermission(code, checked) {
  let values = (form.value.permissions || "").split(",").filter(Boolean);
  values = checked
    ? [...new Set([...values, code])]
    : values.filter((v) => v !== code);
  form.value.permissions = values.join(",");
}
function roleScopeChanged(key) {
  if (key === "scope")
    form.value.permissions =
      form.value.scope === "CUSTOMER"
        ? "portal"
        : form.value.scope === "PARTNER"
          ? "assets,contracts,finance,risk,reports"
          : "assets,contracts,finance,risk,reports";
}
function select(key) {
  if (["scope", "kind", "category"].includes(key)) return true;
  return [
    "org_id",
    "customer_id",
    "asset_id",
    "rate_id",
    "role_id",
    "owner_id",
    "bill_id",
  ].includes(key);
}
function choices(key) {
  if (key === "scope")
    return ["HQ", "PARTNER", "CUSTOMER"].map((value) => ({
      value,
      label: states[value],
    }));
  if (key === "kind")
    return [
      { value: "asset_category", label: "设备分类" },
      { value: "cost_category", label: "费用分类" },
    ];
  if (key === "category")
    return (lookup.value.dictionary || [])
      .filter(
        (x) =>
          x.kind ===
          (modal.value?.resource === "assets"
            ? "asset_category"
            : "cost_category"),
      )
      .map((x) => ({ value: x.code, label: x.label }));
  return options(key);
}
const actionLabels = {
  deliver: "登记设备交付",
  "accept-delivery": "确认设备接收",
  "confirm-return": "确认设备归还",
  "cancel-return": "撤销归还登记",
  allocate: "投放设备",
  accept: "确认接收",
  maintain: "进入维保",
  repair: "完成维保",
  recall: "发起回收",
  receive: "总部接收",
  dispose: "处置设备",
  submit: "提交客户确认",
  confirm: "确认合同",
  cancel: "取消",
  return: "登记归还",
  approve: "审核通过",
  reject: "审核拒绝",
  reverse: "冲正",
  pay: "登记转款",
  unpay: "撤销转款",
  follow: "记录跟进",
  close: "关闭事项",
  reopen: "重新跟进",
  "reset-password": "重置密码",
};
function actions(row) {
  const r = resource.value,
    s = row.state;
  switch (r) {
    case "assets":
      return s === "HQ_AVAILABLE"
        ? hq.value
          ? ["allocate", "maintain", "dispose"]
          : []
        : s === "IN_TRANSIT"
          ? account.value.scope === "PARTNER"
            ? ["accept"]
            : []
          : s === "PARTNER_AVAILABLE"
            ? ["maintain", ...(hq.value ? ["recall"] : [])]
            : s === "MAINTENANCE"
              ? ["repair"]
              : s === "IN_RETURN" && hq.value
                ? ["receive"]
                : [];
    case "contracts":
      return client.value
        ? s === "PENDING"
          ? ["confirm"]
          : s === "ACTIVE" && row.delivery_state === "DISPATCHED"
            ? ["accept-delivery"]
            : s === "ACTIVE" && row.delivery_state === "RETURN_PENDING"
              ? ["confirm-return"]
              : []
        : s === "DRAFT"
          ? ["submit", "cancel"]
          : s === "PENDING"
            ? ["cancel"]
            : s === "ACTIVE" && account.value.scope === "PARTNER"
              ? row.delivery_state === "WAITING"
                ? ["deliver"]
                : row.delivery_state === "ACCEPTED"
                  ? ["return"]
                  : row.delivery_state === "RETURN_PENDING"
                    ? ["cancel-return"]
                    : []
              : [];
    case "receipts":
    case "costs":
      return hq.value && perms.value.includes("approve")
        ? s === "PENDING"
          ? ["approve", "reject"]
          : s === "APPROVED"
            ? ["reverse"]
            : []
        : [];
    case "settlements": {
      const approve = hq.value && perms.value.includes("approve");
      return s === "DRAFT"
        ? [
            ...(approve && Number(row.creator_id) !== Number(account.value.id)
              ? ["approve"]
              : []),
            ...(approve || Number(row.creator_id) === Number(account.value.id)
              ? ["cancel"]
              : []),
          ]
        : s === "APPROVED" && approve
          ? ["pay", "reverse"]
          : s === "PAID" && approve
            ? ["unpay"]
            : [];
    }
    case "risks":
      return s === "OPEN" ? ["follow", "close"] : ["reopen"];
    case "accounts":
      return Number(row.id) !== 1 ? ["reset-password"] : [];
    default:
      return [];
  }
}
function openCreate() {
  modal.value = {
    resource: resource.value,
    title: "新增" + titles[resource.value],
  };
  form.value = {
    org_id: hq.value ? "" : account.value.org_id,
    enabled: true,
    scope: "PARTNER",
    permissions: "assets,contracts,finance,risk,reports",
    kind: "asset_category",
    start_on: today(),
    end_on: new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10),
    effective_on: today(),
    received_on: today(),
    incurred_on: today(),
    next_on: today(),
    month_on: today().slice(0, 7) + "-01",
    hq_bps: "50",
  };
}
function openAction(row, action) {
  modal.value = {
    resource: resource.value,
    id: row.id,
    action,
    title: actionLabels[action],
  };
  form.value = {
    version: row.version,
    note: "",
    return_on: today(),
    delivered_on: today(),
    next_on: today(),
    transferred_on: today(),
  };
  detail.value = null;
}
function openEdit(row) {
  modal.value = {
    resource: resource.value,
    id: row.id,
    edit: true,
    title: "编辑" + titles[resource.value],
  };
  form.value = { ...row };
  detail.value = null;
}
async function showDetail(row) {
  await run(async () => {
    detail.value = await request(`/data/${resource.value}/${row.id}`);
  });
}
async function save() {
  await run(async () => {
    const m = modal.value,
      b = { ...form.value };
    for (const key of fields()) {
      if (key.endsWith("_cents")) b[key] = cents(b[key]);
      if (key === "hq_bps") {
        const s = String(b[key]);
        if (!/^\d+(\.\d{1,2})?$/.test(s))
          throw new Error("分成比例最多两位小数");
        b[key] = Math.round(Number(s) * 100);
      }
      if (key.endsWith("_id") && b[key] !== "" && b[key] != null)
        b[key] = Number(b[key]);
    }
    await request(
      `/data/${m.resource}${m.id ? "/" + m.id : ""}${m.action ? "/" + m.action : ""}`,
      m.edit ? "PUT" : "POST",
      b,
    );
    modal.value = null;
    await load();
    notice.value = "已保存";
  });
}
async function changePassword() {
  modal.value = { resource: "password", title: "修改密码" };
  form.value = { oldPassword: "", newPassword: "" };
}
async function savePassword() {
  await run(async () => {
    await request("/auth/password", "POST", form.value);
    modal.value = null;
    profile.value = null;
    notice.value = "密码已修改，请重新登录";
  });
}
const detailFields = computed(() =>
  Object.entries(detail.value?.record || {}).filter(
    ([k]) => !["version", "password_hash"].includes(k),
  ),
);
const adminTabs = [
  "organizations",
  "accounts",
  "roles",
  "dictionary",
  "settings",
  "menus",
  "audit",
  "transfers",
];
const edits = [
  "organizations",
  "accounts",
  "dictionary",
  "menus",
  "settings",
  "rates",
  "customers",
  "roles",
];
</script>
<template>
  <div v-if="!profile" class="login-shell">
    <section class="login-story">
      <img src="/brand/logo.jpg" alt="知华科技" /><span class="eyebrow"
        >LEASEHUB / EQUIPMENT OPERATIONS</span
      >
      <h1>产业设备租赁<br />总部与合作商工作台</h1>
      <p>投放交接 · 租赁履约 · 收款核销 · 分成结算</p>
      <div class="story-lines">
        <span>01 / 总部统筹资产</span><span>02 / 合作商独立经营</span
        ><span>03 / 客户核对租赁</span>
      </div>
    </section>
    <section class="login-card">
      <span class="eyebrow">知华 LeaseHub</span>
      <h2>登录工作台</h2>
      <p class="muted">使用总部为你开通的账号</p>
      <form @submit.prevent="signIn">
        <label
          >登录名<input
            v-model="login.username"
            autocomplete="username"
            required
            maxlength="60" /></label
        ><label
          >密码<input
            v-model="login.password"
            type="password"
            autocomplete="current-password"
            required
            maxlength="128"
        /></label>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <p v-if="notice" class="success">{{ notice }}</p>
        <button class="primary" :disabled="busy">
          {{ busy ? "正在登录…" : "进入工作台" }}
        </button>
      </form>
      <button class="link" @click="about = true">关于知华科技与源码授权</button>
    </section>
  </div>
  <div v-else class="workspace">
    <aside class="sidebar">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" />
        <div><strong>LeaseHub</strong><small>产业设备租赁协同</small></div>
      </div>
      <div class="scope-label">{{ states[account.scope] }}工作台</div>
      <nav aria-label="业务导航">
        <button
          v-for="(m, i) in menu"
          :key="m.code"
          :class="{ active: view === m.code }"
          @click="navigate(m.code)"
        >
          <span class="nav-number">{{ String(i + 1).padStart(2, "0") }}</span
          >{{ m.label }}
        </button>
      </nav>
      <button class="sidebar-about" @click="about = true">
        知华科技 · 关于与联系 ↗
      </button>
    </aside>
    <main>
      <header class="topbar">
        <div>
          <span class="eyebrow">{{
            profile.settings?.find((s) => s.code === "platform_name")?.value ||
            "知华 LeaseHub"
          }}</span>
          <p>{{ states[account.scope] }} / {{ account.display_name }}</p>
        </div>
        <div class="top-actions">
          <button @click="changePassword">修改密码</button
          ><button @click="signOut">退出登录</button>
        </div>
      </header>
      <section class="content">
        <div class="page-heading">
          <div>
            <span class="eyebrow">OPERATIONS WORKSPACE</span>
            <h1>{{ heading }}</h1>
            <p class="muted">
              {{ hints[view] || "按账号权限管理与查询真实业务记录。" }}
            </p>
          </div>
          <button
            v-if="canCreate && !['overview', 'reports'].includes(view)"
            class="primary"
            :disabled="busy"
            @click="openCreate"
          >
            ＋ 新增
          </button>
        </div>
        <div v-if="error" role="alert" class="error">{{ error }}</div>
        <div v-if="notice" role="status" class="success">{{ notice }}</div>
        <template v-if="['overview', 'reports'].includes(view) && report"
          ><div class="metrics">
            <article>
              <span>合同应收</span
              ><strong>{{ money(report.totals.billed) }}</strong
              ><small>已生效合同生成账单</small>
            </article>
            <article>
              <span>已核销收款</span
              ><strong>{{ money(report.totals.collected) }}</strong
              ><small>不含待审核登记</small>
            </article>
            <article class="warning">
              <span>逾期未收</span
              ><strong>{{ money(report.totals.overdue) }}</strong
              ><small>截至 {{ report.today }}</small>
            </article>
            <article>
              <span>到期待跟进</span
              ><strong
                >{{ report.followups.length
                }}{{ report.followups.length === 100 ? "+" : "" }}</strong
              ><small>风险事项，最多显示100项</small>
            </article>
          </div>
          <div class="dashboard-grid">
            <section class="panel">
              <div class="panel-title">
                <h2>设备分布</h2>
                <button
                  v-if="perms.includes('assets')"
                  class="link"
                  @click="navigate('assets')"
                >
                  查看设备 →
                </button>
              </div>
              <div v-for="s in report.states" :key="s.state" class="state-row">
                <span>{{ pretty("state", s.state) }}</span>
                <div>
                  <i
                    :style="{
                      width:
                        (Number(s.total) /
                          Math.max(
                            1,
                            ...report.states.map((x) => Number(x.total)),
                          )) *
                          100 +
                        '%',
                    }"
                  ></i>
                </div>
                <strong>{{ s.total }}</strong>
              </div>
              <p v-if="!report.states.length" class="empty">
                暂无设备。总部可先登记并投放设备。
              </p>
            </section>
            <section class="panel">
              <h2>审核后结算</h2>
              <table>
                <thead>
                  <tr>
                    <th>月份</th>
                    <th>核销收款</th>
                    <th>总部净应收</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="m in report.months" :key="m.month_on">
                    <td>{{ m.month_on.slice(0, 7) }}</td>
                    <td>{{ money(m.collected_cents) }}</td>
                    <td>{{ money(m.hq_net_cents) }}</td>
                  </tr>
                </tbody>
              </table>
              <p v-if="!report.months.length" class="empty">暂无已审核结算</p>
            </section>
          </div>
          <section class="panel">
            <div class="panel-title">
              <h2>逾期账单</h2>
              <span class="muted">最多显示100条，完整清单见分期账单</span>
            </div>
            <table>
              <thead>
                <tr>
                  <th>账单</th>
                  <th>客户</th>
                  <th>到期日</th>
                  <th>未收余额</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="b in report.overdue" :key="b.id">
                  <td>#{{ b.id }}</td>
                  <td>{{ pretty("customer_id", b.customer_id) }}</td>
                  <td>{{ b.due_on }}</td>
                  <td>
                    {{ money(Number(b.amount_cents) - Number(b.paid_cents)) }}
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="!report.overdue.length" class="empty">暂无逾期未收账单</p>
          </section>
          <div class="dashboard-grid">
            <section class="panel">
              <h2>闲置设备</h2>
              <p v-for="a in report.idle" :key="a.id" class="line-item">
                {{ a.serial }} · {{ a.name
                }}<small>{{ a.changed_on }} 起可用</small>
              </p>
              <p v-if="!report.idle.length" class="empty">
                暂无达到预警天数的闲置设备
              </p>
            </section>
            <section class="panel">
              <h2>今天需要跟进</h2>
              <p v-for="c in report.followups" :key="c.id" class="line-item">
                {{ c.title
                }}<small
                  >{{ pretty("owner_id", c.owner_id) }} · {{ c.next_on }}</small
                >
              </p>
              <p v-if="!report.followups.length" class="empty">
                暂无到期跟进事项
              </p>
            </section>
          </div></template
        >
        <template v-else
          ><div v-if="view === 'admin'" class="tabs">
            <button
              v-for="t in adminTabs"
              :key="t"
              :class="{ selected: adminTab === t }"
              @click="
                adminTab = t;
                navigate('admin');
              "
            >
              {{ titles[t] }}
            </button>
          </div>
          <div v-if="view === 'portal'" class="tabs">
            <button
              v-for="t in ['contracts', 'bills', 'receipts']"
              :key="t"
              :class="{ selected: portalTab === t }"
              @click="
                portalTab = t;
                navigate('portal');
              "
            >
              {{ titles[t] }}
            </button>
          </div>
          <section class="panel">
            <form
              class="filters"
              @submit.prevent="
                page = 1;
                run(load);
              "
            >
              <input
                v-model="search"
                :placeholder="
                  ['assets'].includes(resource)
                    ? '设备名称 / 序列号'
                    : resource === 'accounts'
                      ? '登录名 / 姓名'
                      : '名称、凭据号或编号'
                "
                aria-label="查询关键词"
              /><select
                v-if="
                  [
                    'assets',
                    'contracts',
                    'receipts',
                    'costs',
                    'settlements',
                    'risks',
                  ].includes(resource)
                "
                v-model="state"
                aria-label="状态过滤"
              >
                <option value="">全部状态</option>
                <option
                  v-for="s in resource === 'assets'
                    ? [
                        'HQ_AVAILABLE',
                        'IN_TRANSIT',
                        'PARTNER_AVAILABLE',
                        'RESERVED',
                        'RENTED',
                        'MAINTENANCE',
                        'IN_RETURN',
                        'DISPOSED',
                      ]
                    : resource === 'contracts'
                      ? ['DRAFT', 'PENDING', 'ACTIVE', 'RETURNED', 'CANCELLED']
                      : resource === 'settlements'
                        ? ['DRAFT', 'APPROVED', 'PAID', 'CANCELLED', 'REVERSED']
                        : resource === 'risks'
                          ? ['OPEN', 'CLOSED']
                          : ['PENDING', 'APPROVED', 'REJECTED', 'REVERSED']"
                  :key="s"
                  :value="s"
                >
                  {{ states[s] }}
                </option></select
              ><button :disabled="busy">查询</button
              ><a
                v-if="canExport"
                class="button export"
                :href="'/api/export/' + resource"
                target="_blank"
                rel="noopener"
                >导出 CSV</a
              >
            </form>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th v-for="key in columns[resource]" :key="key">
                      {{ labels[key] || key }}
                    </th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in data.items" :key="row.id">
                    <td v-for="key in columns[resource]" :key="key">
                      <span
                        :class="{
                          badge: key === 'state',
                          negative: key === 'hq_net_cents' && row[key] < 0,
                        }"
                        >{{ pretty(key, row[key]) }}</span
                      >
                    </td>
                    <td class="row-actions">
                      <button @click="showDetail(row)">详情</button
                      ><button
                        v-if="
                          edits.includes(resource) &&
                          !(resource === 'rates' && !hq) &&
                          !(
                            ['accounts', 'roles'].includes(resource) &&
                            Number(row.id) === 1
                          )
                        "
                        @click="openEdit(row)"
                      >
                        编辑</button
                      ><button
                        v-for="a in actions(row)"
                        :key="a"
                        @click="openAction(row, a)"
                      >
                        {{ actionLabels[a] }}
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-if="!data.items.length" class="empty">
              <strong>暂无符合条件的记录</strong>
              <p>
                {{
                  canCreate
                    ? "可新增业务记录，或调整查询条件。"
                    : "请调整查询条件，或等待有权人员办理业务。"
                }}
              </p>
            </div>
            <footer class="pagination">
              <span
                >共 {{ data.total }} 条 · 第 {{ page }} /
                {{ Math.max(1, Math.ceil(data.total / 20)) }} 页</span
              >
              <div>
                <button
                  :disabled="page <= 1 || busy"
                  @click="
                    page--;
                    run(load);
                  "
                >
                  上一页</button
                ><button
                  :disabled="page * 20 >= data.total || busy"
                  @click="
                    page++;
                    run(load);
                  "
                >
                  下一页
                </button>
              </div>
            </footer>
          </section></template
        >
      </section>
    </main>
  </div>
  <div v-if="modal" class="overlay" @click.self="!busy && (modal = null)">
    <section
      class="dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="modal.title"
    >
      <div class="dialog-heading">
        <h2>{{ modal.title }}</h2>
        <button :disabled="busy" @click="modal = null">关闭</button>
      </div>
      <p v-if="modal.action === 'confirm'" class="callout">
        确认代表你已核对该合同的设备、租期及费用。确认后合同生效，账单生成。页面确认不是电子签章。
      </p>
      <p v-if="['pay', 'unpay'].includes(modal.action)" class="callout">
        仅登记已核实的线下转款凭据，不会发起银行转账。
      </p>
      <p
        v-if="modal.resource === 'settlements' && !modal.action"
        class="callout"
      >
        冻结该月已核销收款与已批准成本；审批必须由另一名总部人员完成。补录明细需先撤销旧结算。
      </p>
      <form
        @submit.prevent="
          modal.resource === 'password' ? savePassword() : save()
        "
      >
        <template v-if="modal.resource === 'password'"
          ><label
            >旧密码<input
              v-model="form.oldPassword"
              type="password"
              autocomplete="current-password"
              required /></label
          ><label
            >新密码<input
              v-model="form.newPassword"
              type="password"
              autocomplete="new-password"
              minlength="12"
              maxlength="128"
              required /></label
        ></template>
        <div v-else class="form-grid">
          <component
            :is="key === 'permissions' ? 'div' : 'label'"
            v-for="key in fields()"
            :key="key"
            class="field"
            >{{ labels[key] || key
            }}{{
              key.endsWith("_cents")
                ? "（元）"
                : key === "hq_bps"
                  ? "（%）"
                  : ""
            }}
            <div v-if="key === 'permissions'" class="permission-options">
              <label
                v-for="permission in availablePermissions"
                :key="permission"
                ><input
                  type="checkbox"
                  :checked="
                    (form.permissions || '').split(',').includes(permission)
                  "
                  @change="togglePermission(permission, $event.target.checked)"
                />{{ permissionLabels[permission] }}</label
              >
            </div>
            <select
              v-else-if="select(key)"
              v-model="form[key]"
              @change="roleScopeChanged(key)"
              :required="!(key === 'asset_id' && modal.resource === 'risks')"
            >
              <option value="">
                请选择{{
                  key === "asset_id" && modal.resource === "risks"
                    ? "（可不关联）"
                    : ""
                }}
              </option>
              <option
                v-for="opt in choices(key)"
                :key="opt.value"
                :value="opt.value"
              >
                {{ opt.label }}
              </option></select
            ><input
              v-else-if="key === 'enabled'"
              v-model="form[key]"
              type="checkbox"
            /><textarea
              v-else-if="key === 'note'"
              v-model="form[key]"
              required
              maxlength="400"
              rows="3"
            ></textarea
            ><input
              v-else
              v-model="form[key]"
              :type="
                key === 'password'
                  ? 'password'
                  : key.endsWith('_on')
                    ? 'date'
                    : key.endsWith('_cents') ||
                        key === 'hq_bps' ||
                        key === 'position'
                      ? 'number'
                      : 'text'
              "
              :step="
                key.endsWith('_cents') || key === 'hq_bps' ? '0.01' : undefined
              "
              :min="key.endsWith('_cents') ? '0.01' : undefined"
              :maxlength="
                ['permissions'].includes(key)
                  ? 1000
                  : [
                        'name',
                        'display_name',
                        'reference',
                        'title',
                        'contact',
                        'label',
                      ].includes(key)
                    ? 120
                    : 128
              "
              :autocomplete="key === 'password' ? 'new-password' : 'off'"
              required
            /><small v-if="key === 'password'"
              >12–128位，含大小写字母及数字。</small
            >
            <small v-if="key === 'permissions'"
              >按岗位选择功能；客户仅可访问本人租赁。</small
            ></component
          >
        </div>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <div class="dialog-footer">
          <button type="button" :disabled="busy" @click="modal = null">
            取消</button
          ><button class="primary" :disabled="busy">
            {{ busy ? "正在提交…" : "确认保存" }}
          </button>
        </div>
      </form>
    </section>
  </div>
  <div v-if="detail" class="overlay" @click.self="detail = null">
    <section
      class="dialog wide"
      role="dialog"
      aria-modal="true"
      aria-label="记录详情"
    >
      <div class="dialog-heading">
        <h2>{{ titles[resource] }} · #{{ detail.record.id }}</h2>
        <button @click="detail = null">关闭</button>
      </div>
      <dl class="details">
        <template v-for="[key, val] in detailFields" :key="key"
          ><dt>{{ labels[key] || key }}</dt>
          <dd>{{ pretty(key, val) }}</dd></template
        >
      </dl>
      <p v-if="resource === 'contracts'" class="callout">
        日租金按冻结费率计算，结束日不计费；归还不自动免除账单。待确认合同尚未出账。
      </p>
      <p v-if="resource === 'settlements'" class="callout">
        总部净应收为正：合作商向总部转款；为负：总部向合作商转款。成本由总部承担。
      </p>
      <section v-if="detail.items.length">
        <h3>
          {{
            resource === "contracts"
              ? "分期账单"
              : resource === "settlements"
                ? "冻结结算明细"
                : "历史记录"
          }}
        </h3>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th
                  v-for="key in Object.keys(detail.items[0]).filter(
                    (k) =>
                      !['id', 'asset_id', 'case_id', 'settlement_id'].includes(
                        k,
                      ),
                  )"
                  :key="key"
                >
                  {{ labels[key] || key }}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in detail.items" :key="item.id || item.start_on">
                <td
                  v-for="key in Object.keys(item).filter(
                    (k) =>
                      !['id', 'asset_id', 'case_id', 'settlement_id'].includes(
                        k,
                      ),
                  )"
                  :key="key"
                >
                  {{ pretty(key, item[key]) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <section v-if="detail.events?.length">
        <h3>合同与交接记录</h3>
        <p v-for="e in detail.events" :key="e.id" class="line-item">
          {{ actionLabels[e.action] || e.action }} ·
          {{ pretty("actor_id", e.actor_id)
          }}<small>{{ e.created_at }} · {{ e.note }}</small>
        </p>
      </section>
      <section v-if="detail.transfers.length">
        <h3>转款与冲正流水</h3>
        <p v-for="t in detail.transfers" :key="t.id" class="line-item">
          {{ pretty("action", t.action) }} ·
          {{ pretty("direction", t.direction) }} · {{ money(t.amount_cents)
          }}<small
            >{{ t.transferred_on }} · {{ t.reference }} · {{ t.note }}</small
          >
        </p>
      </section>
      <div class="dialog-footer">
        <button
          v-for="a in actions(detail.record)"
          :key="a"
          @click="openAction(detail.record, a)"
        >
          {{ actionLabels[a] }}
        </button>
      </div>
    </section>
  </div>
  <div v-if="about" class="overlay" @click.self="about = false">
    <section
      class="dialog"
      role="dialog"
      aria-modal="true"
      aria-label="关于知华科技"
    >
      <div class="dialog-heading">
        <h2>关于知华科技</h2>
        <button @click="about = false">关闭</button>
      </div>
      <img class="about-logo" src="/brand/logo.jpg" alt="知华科技" />
      <p>知华科技（上海如静知华信息科技有限公司）</p>
      <p>源码公开、非商业使用；仅限个人学习交流，未经公司书面授权不得商用。</p>
      <p>
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >www.zhuatech.cn ↗</a
        >
      </p>
      <p>商业授权或深度定制开发请联系知华科技。</p>
      <p>微信 zhuatech / zhuatech2</p>
    </section>
  </div>
</template>
