import type { CardDef } from '../battle/types.js';
import { EXPANSION_CARDS } from './catalog-expansion.js';

/** Data-driven V1 card catalog — one distinctive hook per theme. */
const BASE_CARDS: CardDef[] = [
  // —— Vanilla: high stats, few keywords ——
  { id: 'van_grunt', name: 'Grunt', nameZh: '苦力怕民兵', theme: 'vanilla', kind: 'unit', cost: 1, attack: 2, health: 2, textZh: '白板单位，数值扎实。', art: 'van_grunt.png' },
  { id: 'van_knight', name: 'Iron Knight', nameZh: '铁骑士', theme: 'vanilla', kind: 'unit', cost: 3, attack: 4, health: 4, textZh: '高攻高血白板。' },
  { id: 'van_golem', name: 'Stone Golem', nameZh: '石傀儡', theme: 'vanilla', kind: 'unit', cost: 5, attack: 5, health: 7, textZh: '厚血前线。' },
  { id: 'van_archer', name: 'Archer', nameZh: '弓箭手', theme: 'vanilla', kind: 'unit', cost: 2, attack: 3, health: 2, textZh: '廉价输出。' },
  { id: 'van_smite', name: 'Smite', nameZh: '惩戒', theme: 'vanilla', kind: 'spell', cost: 2, textZh: '对敌方一个单位造成 3 点伤害。', effect: { id: 'damage_unit', target: 'enemy_unit', amount: 3 } },
  { id: 'van_heal', name: 'Bandage', nameZh: '绷带', theme: 'vanilla', kind: 'spell', cost: 1, textZh: '治疗己方一个单位 3 点生命。', effect: { id: 'heal_unit', target: 'friendly_unit', amount: 3 } },
  { id: 'van_rally', name: 'Rally', nameZh: '集结', theme: 'vanilla', kind: 'spell', cost: 3, textZh: '所有己方单位 +1 攻直到回合结束（简化：永久 +1）。', effect: { id: 'buff_all', amount: 1, amount2: 0 } },
  { id: 'van_titan', name: 'Village Titan', nameZh: '村庄泰坦', theme: 'vanilla', kind: 'unit', cost: 7, attack: 8, health: 8, textZh: '终局白板。' },
  { id: 'van_scout', name: 'Scout', nameZh: '侦察兵', theme: 'vanilla', kind: 'unit', cost: 1, attack: 1, health: 3, textZh: '挡刀用。' },
  { id: 'van_wolf', name: 'Wolf Pack', nameZh: '狼群', theme: 'vanilla', kind: 'unit', cost: 2, attack: 3, health: 3, textZh: '性价比单位。' },

  // —— GT: machines + capacitor ——
  { id: 'gt_lv_machine', name: 'LV Assembler', nameZh: 'LV 组装机', theme: 'gt', kind: 'structure', cost: 2, health: 4, keywords: ['machine'], manaPerTurn: 1, textZh: '占格机器，每回合 +1 费。', art: 'gt_lv_machine.png' },
  { id: 'gt_mv_machine', name: 'MV Centrifuge', nameZh: 'MV 离心机', theme: 'gt', kind: 'structure', cost: 3, health: 5, keywords: ['machine'], manaPerTurn: 2, textZh: '每回合 +2 费。', art: 'gt_mv_machine.png' },
  { id: 'gt_capacitor', name: 'Lapotronic Capacitor', nameZh: '蓝波顿电容库', theme: 'gt', kind: 'structure', cost: 3, health: 6, keywords: ['machine', 'capacitor'], textZh: '回合结束未用费用入库；超额爆炸。', art: 'gt_capacitor.png' },
  { id: 'gt_worker', name: 'GT Worker', nameZh: 'GT 工人', theme: 'gt', kind: 'unit', cost: 2, attack: 2, health: 3, textZh: '基础护卫。', art: 'gt_worker.png' },
  { id: 'gt_drill', name: 'Miner Drill', nameZh: '采矿钻头', theme: 'gt', kind: 'unit', cost: 4, attack: 4, health: 3, textZh: '中期输出。', art: 'gt_drill.png' },
  { id: 'gt_overclock', name: 'Overclock', nameZh: '超频', theme: 'gt', kind: 'spell', cost: 1, textZh: '本回合多获得 2 费（简化：立刻 +2）。', art: 'gt_overclock.png', effect: { id: 'gain_mana', amount: 2 } },
  { id: 'gt_wrench', name: 'Wrench Bash', nameZh: '扳手猛击', theme: 'gt', kind: 'spell', cost: 2, textZh: '拆除敌方一个机器结构。', art: 'gt_wrench.png', effect: { id: 'destroy_machine', target: 'enemy_machine' } },
  { id: 'gt_battery', name: 'Battery Buffer', nameZh: '电池箱', theme: 'gt', kind: 'structure', cost: 1, health: 3, keywords: ['machine', 'capacitor'], textZh: '小型电容，过载阈值更低。', art: 'gt_battery.png' },
  { id: 'gt_hv_turbine', name: 'HV Turbine', nameZh: 'HV 涡轮', theme: 'gt', kind: 'structure', cost: 5, health: 7, keywords: ['machine'], manaPerTurn: 3, textZh: '高产费机器。', art: 'gt_hv_turbine.png' },
  { id: 'gt_engineer', name: 'Engineer', nameZh: '工程师', theme: 'gt', kind: 'unit', cost: 3, attack: 2, health: 5, textZh: '肉盾技工。', art: 'gt_engineer.png' },

  // —— Thaumcraft: aspects + swap ——
  { id: 'th_zombie', name: 'Angry Zombie', nameZh: '红眼僵尸', theme: 'thaum', kind: 'unit', cost: 2, attack: 3, health: 3, keywords: ['aspect'], aspects: ['perditio'], textZh: '可挂载源质。', art: 'th_zombie.png' },
  { id: 'th_wisp', name: 'Wisp', nameZh: '幽魂', theme: 'thaum', kind: 'unit', cost: 1, attack: 1, health: 2, keywords: ['aspect'], aspects: ['aer'], textZh: '风要素载体。', art: 'th_wisp.png' },
  { id: 'th_golem', name: 'Thaumic Golem', nameZh: '神秘傀儡', theme: 'thaum', kind: 'unit', cost: 4, attack: 3, health: 5, keywords: ['aspect'], aspects: ['ordo'], textZh: '秩序载体。', art: 'th_golem.png' },
  { id: 'th_boss', name: 'Boss Crimson', nameZh: '绯红邪术师', theme: 'thaum', kind: 'unit', cost: 6, attack: 5, health: 6, keywords: ['aspect'], aspects: ['ignis', 'perditio'], textZh: '神秘 Boss。', art: 'th_boss.png' },
  { id: 'th_ordo_aer', name: 'Ordo+Aer Infusion', nameZh: '秩序·风 注魔', theme: 'thaum', kind: 'spell', cost: 2, textZh: '给单位挂 秩序+风：格挡阶段可交换己方两个槽位。', art: 'th_ordo_aer.png', effect: { id: 'add_aspects', target: 'friendly_unit', aspects: ['ordo', 'aer'] } },
  { id: 'th_ignis', name: 'Ignis Bolt', nameZh: '火源质弹', theme: 'thaum', kind: 'spell', cost: 2, textZh: '造成 2 点伤害并附加 ignis。', art: 'th_ignis.png', effect: { id: 'damage_and_aspect', target: 'enemy_unit', amount: 2, aspects: ['ignis'] } },
  { id: 'th_ward', name: 'Warding', nameZh: '护盾结界', theme: 'thaum', kind: 'spell', cost: 1, textZh: '单位 +2 甲。', art: 'th_ward.png', effect: { id: 'armor_unit', target: 'friendly_unit', amount: 2 } },
  { id: 'th_node', name: 'Aura Node', nameZh: '灵气节点', theme: 'thaum', kind: 'structure', cost: 3, health: 4, keywords: ['aspect'], textZh: '每回合给随机己方单位挂一个源质（简化：+ordo）。', art: 'th_node.png' },
  { id: 'th_cultist', name: 'Cultist', nameZh: '邪徒', theme: 'thaum', kind: 'unit', cost: 2, attack: 2, health: 2, keywords: ['aspect'], aspects: ['perditio'], textZh: '廉价邪术单位。', art: 'th_cultist.png' },
  { id: 'th_eldritch', name: 'Eldritch Construct', nameZh: '邪术构造', theme: 'thaum', kind: 'unit', cost: 5, attack: 4, health: 5, keywords: ['aspect'], aspects: ['ordo', 'terra'], textZh: '高阶构造体。', art: 'th_eldritch.png' },

  // —— Forestry: beehive ——
  { id: 'fo_hive', name: 'Apiary', nameZh: '蜂箱', theme: 'forestry', kind: 'structure', cost: 3, health: 5, keywords: ['beehive', 'untargetable'], hiveCooldown: 3, textZh: '占格不可被攻；冷却结束后产蜂/合并。' },
  { id: 'fo_bee', name: 'Honey Bee', nameZh: '小蜜蜂', theme: 'forestry', kind: 'unit', cost: 1, attack: 1, health: 1, keywords: ['bee'], textZh: '可由蜂箱合并成长。' },
  { id: 'fo_plugin_speed', name: 'Frame Accelerator', nameZh: '加速框架', theme: 'forestry', kind: 'spell', cost: 1, textZh: '蜂箱冷却 -1。', effect: { id: 'hive_cooldown', target: 'none', amount: 1 } },
  { id: 'fo_plugin_strong', name: 'Mutator', nameZh: '突变器', theme: 'forestry', kind: 'spell', cost: 2, textZh: '下一只蜂获得吸血或 AOE（随机）。', effect: { id: 'buff_unit', target: 'none', amount: 0, amount2: 0 } },
  { id: 'fo_stealth_bee', name: 'Shadow Bee', nameZh: '隐秘蜂', theme: 'forestry', kind: 'unit', cost: 3, attack: 2, health: 2, keywords: ['bee', 'stealth'], textZh: '无隐秘单位无法格挡它。' },
  { id: 'fo_royal', name: 'Princess', nameZh: '蜂后', theme: 'forestry', kind: 'unit', cost: 4, attack: 3, health: 4, keywords: ['bee', 'lifesteal'], textZh: '吸血蜂。' },
  { id: 'fo_aoe_bee', name: 'Bombastic Bee', nameZh: '爆破蜂', theme: 'forestry', kind: 'unit', cost: 4, attack: 2, health: 3, keywords: ['bee', 'aoe'], textZh: '攻击溅射左右格。' },
  { id: 'fo_keeper', name: 'Beekeeper', nameZh: '养蜂人', theme: 'forestry', kind: 'unit', cost: 2, attack: 2, health: 3, textZh: '护卫蜂箱。' },
  { id: 'fo_alveary', name: 'Alveary', nameZh: '蜂房', theme: 'forestry', kind: 'structure', cost: 5, health: 8, keywords: ['beehive', 'untargetable'], hiveCooldown: 2, textZh: '更快产蜂。' },
  { id: 'fo_smoke', name: 'Smoke Gun', nameZh: '烟雾枪', theme: 'forestry', kind: 'spell', cost: 1, textZh: '移除敌方一个隐秘。', effect: { id: 'strip_stealth', target: 'enemy_stealth' } },

  // —— Astral: cheap utility + nexus buffs ——
  { id: 'as_shield', name: 'Stellar Ward', nameZh: '星辉护盾', theme: 'astral', kind: 'spell', cost: 1, textZh: '玩家减伤 +10%（可叠）。', effect: { id: 'damage_reduction', amount: 10 } },
  { id: 'as_reflect', name: 'Star Reflection', nameZh: '星辉反射', theme: 'astral', kind: 'spell', cost: 2, textZh: '本局玩家受伤反弹到对方玩家。', effect: { id: 'reflect' } },
  { id: 'as_acolyte', name: 'Acolyte', nameZh: '星辉侍僧', theme: 'astral', kind: 'unit', cost: 1, attack: 1, health: 2, textZh: '便宜单位。' },
  { id: 'as_crystal', name: 'Rock Crystal', nameZh: '星辉水晶', theme: 'astral', kind: 'structure', cost: 2, health: 3, textZh: '每回合治疗 Nexus 1。' },
  { id: 'as_attune', name: 'Attunement', nameZh: '调谐', theme: 'astral', kind: 'spell', cost: 1, textZh: '抽 1 张牌。', effect: { id: 'draw', amount: 1 } },
  { id: 'as_ritual', name: 'Celestial Ritual', nameZh: '天体仪式', theme: 'astral', kind: 'spell', cost: 3, textZh: '全体己方单位 +1/+1。', effect: { id: 'buff_all', amount: 1, amount2: 1 } },
  { id: 'as_knight', name: 'Stellar Knight', nameZh: '星辉骑士', theme: 'astral', kind: 'unit', cost: 3, attack: 3, health: 3, keywords: ['lifesteal'], textZh: '配合反射的吸血单位。' },
  { id: 'as_nova', name: 'Nova Burst', nameZh: '新星爆发', theme: 'astral', kind: 'spell', cost: 2, textZh: '对敌方 Nexus 造成 2 点伤害。', effect: { id: 'nexus_damage', amount: 2 } },
  { id: 'as_mantle', name: 'Mantle', nameZh: '星辉披风', theme: 'astral', kind: 'unit', cost: 2, attack: 2, health: 2, textZh: '实用白板。' },
  { id: 'as_lens', name: 'Lens', nameZh: '星辉透镜', theme: 'astral', kind: 'spell', cost: 0, textZh: '本回合费用 +1。', effect: { id: 'gain_mana', amount: 1 } },

  // —— Avaritia: singularity line ——
  { id: 'av_matter', name: 'Neutronium Dust', nameZh: '中子素尘', theme: 'avaritia', kind: 'unit', cost: 1, attack: 1, health: 1, textZh: '朴素前期。' },
  { id: 'av_collector', name: 'Collector', nameZh: '收集者', theme: 'avaritia', kind: 'unit', cost: 2, attack: 1, health: 3, textZh: '苟前期。' },
  { id: 'av_singularity', name: 'Singularity', nameZh: '奇点', theme: 'avaritia', kind: 'spell', cost: 4, keywords: ['singularity'], textZh: '记录奇点进度（需集齐）。', effect: { id: 'singularity' } },
  { id: 'av_singularity_2', name: 'Iron Singularity', nameZh: '铁奇点', theme: 'avaritia', kind: 'spell', cost: 4, keywords: ['singularity'], textZh: '奇点进度 +1。', effect: { id: 'singularity' } },
  { id: 'av_singularity_3', name: 'Gold Singularity', nameZh: '金奇点', theme: 'avaritia', kind: 'spell', cost: 5, keywords: ['singularity'], textZh: '奇点进度 +1。', effect: { id: 'singularity' } },
  { id: 'av_eternal', name: 'Eternal Singularity', nameZh: '永恒奇点', theme: 'avaritia', kind: 'spell', cost: 8, keywords: ['eternal_singularity'], textZh: '需已打出 3 个奇点：激活即死斩杀链路。', effect: { id: 'eternal' } },
  { id: 'av_sword', name: 'Skullfire Sword', nameZh: '骷髅之剑', theme: 'avaritia', kind: 'unit', cost: 6, attack: 6, health: 4, textZh: '成型前的朴素大单位。' },
  { id: 'av_armor', name: 'Infinity Breastplate', nameZh: '无尽胸甲', theme: 'avaritia', kind: 'spell', cost: 3, textZh: 'Nexus +5 最大生命并治疗 5。', effect: { id: 'nexus_max_heal', amount: 5 } },
  { id: 'av_catalyst', name: 'Catalyst', nameZh: '催化剂', theme: 'avaritia', kind: 'spell', cost: 2, textZh: '抽 2，弃 1（简化：抽 1）。', effect: { id: 'draw', amount: 1 } },
  { id: 'av_pawn', name: 'Pawn', nameZh: '棋子', theme: 'avaritia', kind: 'unit', cost: 1, attack: 1, health: 2, textZh: '挡刀。' },

  // —— EE: accelerator ——
  { id: 'ee_relay', name: 'Energy Relay', nameZh: '能量中继', theme: 'ee', kind: 'structure', cost: 2, health: 3, keywords: ['accelerator'], textZh: '可对友方蜂箱/机器加速 1 回合。' },
  { id: 'ee_watch', name: 'Swiftwolf Watch', nameZh: '迅狼怀表', theme: 'ee', kind: 'spell', cost: 2, keywords: ['accelerator'], textZh: '目标结构冷却 -2。', effect: { id: 'hive_cooldown', target: 'friendly_cooldown', amount: 2 } },
  { id: 'ee_trans', name: 'Transmutation', nameZh: '转化', theme: 'ee', kind: 'spell', cost: 1, textZh: '抽 1 张。', effect: { id: 'draw', amount: 1 } },
  { id: 'ee_dark_matter', name: 'Dark Matter', nameZh: '暗物质', theme: 'ee', kind: 'unit', cost: 3, attack: 3, health: 3, textZh: '中期单位。' },
  { id: 'ee_rm', name: 'Red Matter', nameZh: '红物质', theme: 'ee', kind: 'unit', cost: 5, attack: 5, health: 4, textZh: '高费输出。' },
  { id: 'ee_furnace', name: 'DM Furnace', nameZh: '暗物质熔炉', theme: 'ee', kind: 'structure', cost: 3, health: 4, manaPerTurn: 1, textZh: '小产费。' },
  { id: 'ee_klein', name: 'Klein Star', nameZh: '克莱因之星', theme: 'ee', kind: 'spell', cost: 2, textZh: '+3 费本回合。', effect: { id: 'gain_mana', amount: 3 } },
  { id: 'ee_phil', name: 'Philosophers Stone', nameZh: '贤者之石', theme: 'ee', kind: 'spell', cost: 3, textZh: '复制手牌最左边一张到牌库顶（简化：抽同主题随机）。', effect: { id: 'draw', amount: 1 } },
  { id: 'ee_catalyst', name: 'EMC Catalyst', nameZh: 'EMC 催化', theme: 'ee', kind: 'spell', cost: 1, textZh: '治疗 Nexus 2。', effect: { id: 'nexus_heal', amount: 2 } },
  { id: 'ee_guard', name: 'EMC Guard', nameZh: 'EMC 卫士', theme: 'ee', kind: 'unit', cost: 2, attack: 2, health: 4, textZh: '肉盾。' },

  // —— Genetics: swarm ——
  { id: 'ge_larva', name: 'Larva', nameZh: '幼体', theme: 'genetics', kind: 'unit', cost: 0, attack: 1, health: 1, textZh: '零费铺场。' },
  { id: 'ge_drone', name: 'Drone', nameZh: '无人机', theme: 'genetics', kind: 'unit', cost: 1, attack: 1, health: 1, textZh: '铺场。' },
  { id: 'ge_soldier', name: 'Gene Soldier', nameZh: '基因士兵', theme: 'genetics', kind: 'unit', cost: 2, attack: 2, health: 2, textZh: '基础兵。' },
  { id: 'ge_mutate', name: 'Mutation', nameZh: '基因突变', theme: 'genetics', kind: 'spell', cost: 1, textZh: '一个单位 +1/+1。', effect: { id: 'buff_unit', target: 'friendly_unit', amount: 1, amount2: 1 } },
  { id: 'ge_clone', name: 'Clone', nameZh: '克隆', theme: 'genetics', kind: 'spell', cost: 2, textZh: '在空位复制一个己方单位（1/1 复制体）。', effect: { id: 'clone_unit', target: 'friendly_unit_clone' } },
  { id: 'ge_swarm', name: 'Swarm Call', nameZh: '虫群召唤', theme: 'genetics', kind: 'spell', cost: 3, textZh: '召唤两个 1/1 幼体。', effect: { id: 'summon_tokens', tokenCardId: 'ge_larva', tokenCount: 2 } },
  { id: 'ge_tank', name: 'Bio Tank', nameZh: '生化坦克', theme: 'genetics', kind: 'unit', cost: 4, attack: 3, health: 5, textZh: '少数高血单位。' },
  { id: 'ge_spore', name: 'Spore', nameZh: '孢子', theme: 'genetics', kind: 'unit', cost: 1, attack: 1, health: 2, textZh: '挡刀孢子。' },
  { id: 'ge_alpha', name: 'Alpha', nameZh: '阿尔法', theme: 'genetics', kind: 'unit', cost: 5, attack: 4, health: 4, textZh: '虫群领袖。' },
  { id: 'ge_split', name: 'Cell Split', nameZh: '细胞分裂', theme: 'genetics', kind: 'spell', cost: 2, textZh: '抽 2 张单位牌（简化：抽 2）。', effect: { id: 'draw', amount: 2 } },

  // —— AE: off-deck generation ——
  { id: 'ae_inscriber', name: 'Inscriber', nameZh: '压印器', theme: 'ae', kind: 'structure', cost: 2, health: 4, textZh: '每回合可生成一张地区外卡到手牌。' },
  { id: 'ae_craft', name: 'Crafting CPU', nameZh: '合成 CPU', theme: 'ae', kind: 'spell', cost: 2, textZh: '立刻随机生成一张不在卡组内的牌。', effect: { id: 'ae_generate' } },
  { id: 'ae_fluix', name: 'Fluix Guard', nameZh: '福鲁伊克斯卫士', theme: 'ae', kind: 'unit', cost: 3, attack: 3, health: 3, textZh: 'AE 基础单位。' },
  { id: 'ae_p2p', name: 'P2P Tunnel', nameZh: 'P2P 通道', theme: 'ae', kind: 'spell', cost: 1, textZh: '抽 1。', effect: { id: 'draw', amount: 1 } },
  { id: 'ae_cell', name: 'Storage Cell', nameZh: '存储元件', theme: 'ae', kind: 'spell', cost: 3, textZh: '手牌上限外额外留 1（简化：抽 2）。', effect: { id: 'draw', amount: 2 } },
  { id: 'ae_annihilation', name: 'Annihilation Plane', nameZh: '破坏面板', theme: 'ae', kind: 'spell', cost: 2, textZh: '对单位造成 2 伤害。', effect: { id: 'damage_unit', target: 'enemy_unit', amount: 2 } },
  { id: 'ae_formation', name: 'Formation Plane', nameZh: '成型面板', theme: 'ae', kind: 'spell', cost: 2, textZh: '召唤一个 2/2 物质球。', effect: { id: 'summon_token', tokenCardId: 'ae_matter', tokenCount: 1 } },
  { id: 'ae_controller', name: 'Controller', nameZh: 'ME 控制器', theme: 'ae', kind: 'structure', cost: 4, health: 6, manaPerTurn: 1, textZh: '产费 + 地区外卡引擎。' },
  { id: 'ae_matter', name: 'Matter Ball', nameZh: '物质球', theme: 'ae', kind: 'unit', cost: 2, attack: 2, health: 2, textZh: '成型产物。' },
  { id: 'ae_wireless', name: 'Wireless', nameZh: '无线终端', theme: 'ae', kind: 'spell', cost: 1, textZh: '查看并拿取地区外卡池一张。', effect: { id: 'ae_generate' } },

  // —— DLB: force swap ——
  { id: 'dlb_tantrum', name: 'Tantrum', nameZh: '巨婴发作', theme: 'dlb', kind: 'spell', cost: 2, textZh: '立刻强制攻防转换（本回合变为对方攻击）。', effect: { id: 'steal_attack_token' } },
  { id: 'dlb_ignore', name: 'Rule Ignore', nameZh: '无视规则', theme: 'dlb', kind: 'spell', cost: 3, textZh: '本回合你的攻击无视格挡顺序限制一次（简化：对 Nexus 直伤 3）。', effect: { id: 'nexus_damage', amount: 3 } },
  { id: 'dlb_cry', name: 'Cry', nameZh: '哭闹', theme: 'dlb', kind: 'unit', cost: 1, attack: 1, health: 2, textZh: '吵闹单位。' },
  { id: 'dlb_chaos', name: 'Chaos Totem', nameZh: '混乱图腾', theme: 'dlb', kind: 'structure', cost: 3, health: 5, textZh: 'DLB 强制换攻防间隔 -1（最低 3）。' },
  { id: 'dlb_bully', name: 'Bully', nameZh: '恶霸', theme: 'dlb', kind: 'unit', cost: 3, attack: 4, health: 2, textZh: '高攻低血。' },
  { id: 'dlb_mood', name: 'Mood Swing', nameZh: '情绪摇摆', theme: 'dlb', kind: 'spell', cost: 1, textZh: '交换双方一个随机单位位置（简化：对随机敌方单位造成 1）。', effect: { id: 'random_enemy_damage', amount: 1 } },
  { id: 'dlb_nap', name: 'Nap', nameZh: '小睡', theme: 'dlb', kind: 'spell', cost: 2, textZh: '跳过敌方下个攻击声明（简化：敌方本回合 -1 费）。', effect: { id: 'enemy_lose_mana', amount: 1 } },
  { id: 'dlb_toy', name: 'Broken Toy', nameZh: '坏掉的玩具', theme: 'dlb', kind: 'unit', cost: 2, attack: 2, health: 3, textZh: '普通单位。' },
  { id: 'dlb_scream', name: 'Scream', nameZh: '尖叫', theme: 'dlb', kind: 'spell', cost: 4, textZh: '强制换攻防并抽 1。', effect: { id: 'steal_attack_token', amount: 1 } },
  { id: 'dlb_guardian', name: 'Nanny', nameZh: '保姆', theme: 'dlb', kind: 'unit', cost: 4, attack: 2, health: 6, textZh: '肉盾。' },
];

export const CARD_CATALOG: CardDef[] = [...BASE_CARDS, ...EXPANSION_CARDS];

const FAST_SPELLS = new Set([
  'van_smite',
  'van_heal',
  'th_ignis',
  'th_ward',
  'fo_smoke',
  'as_nova',
  'ae_annihilation',
  'dlb_ignore',
  'dlb_mood',
]);

const BURST_SPELLS = new Set([
  'gt_overclock',
  'th_ordo_aer',
  'fo_plugin_speed',
  'fo_plugin_strong',
  'as_shield',
  'as_attune',
  'as_lens',
  'av_catalyst',
  'ee_trans',
  'ee_klein',
  'ee_catalyst',
  'ge_mutate',
  'ge_split',
  'ae_craft',
  'ae_p2p',
  'ae_wireless',
]);

const EXACT_RULES_ZH: Partial<Record<string, string>> = {
  van_smite: '对一个可选中的敌方单位造成 3 点伤害。',
  van_heal: '使一个己方单位恢复 3 点生命，不能超过其最大生命。',
  van_rally: '所有己方非结构单位永久获得 +1 攻击。',
  gt_lv_machine: '轮次结束时，为其控制者生成 1 点普通法力。',
  gt_mv_machine: '轮次结束时，为其控制者生成 2 点普通法力。',
  gt_hv_turbine: '轮次结束时，为其控制者生成 3 点普通法力。',
  gt_capacitor: '轮次结束时，在法术法力结算后储存剩余普通法力。储能超过 10 时降至 5，摧毁一台己方机器，并且每点过载对己方 Nexus 造成 2 点伤害。',
  gt_battery: '允许轮次结束时储存剩余普通法力，但把储能上限设为 4。超过上限时降至 2，摧毁一台己方机器，并且每点过载对己方 Nexus 造成 2 点伤害。',
  gt_overclock: '立即获得 2 点普通法力。该法力可超过本轮法力上限。',
  gt_wrench: '摧毁一个敌方机器结构。',
  th_ordo_aer: '使一个己方单位永久获得秩序与风源质。它的控制者在格挡确认后可进行一次己方槽位换位。',
  th_ignis: '对一个可选中的敌方单位造成 2 点伤害，并使其永久获得火源质。',
  th_ward: '使一个己方单位获得 2 点护甲。护甲会优先吸收伤害。',
  th_node: '轮次结束时，使一个随机己方非结构单位永久获得秩序源质。',
  fo_hive: '不可被敌方效果选中，不能攻击或格挡。每 3 个轮次结束生成一只 1/1 小蜜蜂；若场上已有基础 1/1 蜜蜂，则改为使其永久 +1/+1。',
  fo_alveary: '不可被敌方效果选中，不能攻击或格挡。每 2 个轮次结束生成一只 1/1 小蜜蜂；若场上已有基础 1/1 蜜蜂，则改为使其永久 +1/+1。',
  fo_plugin_speed: '使所有己方蜂箱的当前冷却减少 1，最低为 0。',
  fo_plugin_strong: '下一只由己方蜂箱生成的蜜蜂随机获得吸血或溅射。',
  fo_smoke: '移除一个敌方单位的隐秘关键词。',
  as_shield: '本局己方 Nexus 获得 10% 伤害减免，最多累计至 50%。',
  as_reflect: '本局己方 Nexus 受到战斗伤害时，对敌方 Nexus 反弹该次伤害的 50%，最低 1 点。',
  as_crystal: '轮次结束时，使己方 Nexus 恢复 1 点生命。',
  as_attune: '抽 1 张牌。',
  as_ritual: '所有己方非结构单位永久获得 +1/+1。',
  as_nova: '对敌方 Nexus 造成 2 点基础伤害；最终数值受双方电压与目标减伤影响。',
  as_lens: '立即获得 1 点普通法力。该法力可超过本轮法力上限。',
  av_singularity: '使本局奇点进度增加 1。',
  av_singularity_2: '使本局奇点进度增加 1。',
  av_singularity_3: '使本局奇点进度增加 1。',
  av_eternal: '仅能在奇点进度达到 3 后打出。本局己方攻击单位会立即消灭其格挡者；未被格挡时至少对 Nexus 造成 999 点伤害。',
  av_armor: '使己方 Nexus 的最大生命增加 5，并恢复 5 点生命。',
  av_catalyst: '抽 1 张牌。',
  ee_relay: '轮次结束时，使第一个仍有冷却的己方蜂箱冷却减少 1。',
  ee_watch: '使一个具有冷却的己方结构的当前冷却减少 2，最低为 0。',
  ee_trans: '抽 1 张牌。',
  ee_furnace: '轮次结束时，为其控制者生成 1 点普通法力。',
  ee_klein: '立即获得 3 点普通法力。该法力可超过本轮法力上限。',
  ee_phil: '抽 1 张牌。',
  ee_catalyst: '使己方 Nexus 恢复 2 点生命，不能超过其最大生命。',
  ge_mutate: '使一个己方单位永久获得 +1/+1。',
  ge_clone: '选择一个己方单位，在第一个空槽召唤它的 1/1 复制体；复制关键词与源质，但不复制装备。',
  ge_swarm: '在前两个空槽各召唤一只 1/1 幼体。',
  ge_split: '抽 2 张牌。',
  ae_inscriber: '轮次结束时，随机生成一张不在初始牌组中的非 AE 卡牌到手牌。',
  ae_craft: '随机生成一张不在初始牌组中的非 AE 卡牌到手牌。',
  ae_p2p: '抽 1 张牌。',
  ae_cell: '抽 2 张牌。',
  ae_annihilation: '对一个可选中的敌方单位造成 2 点伤害。',
  ae_formation: '在第一个空槽召唤一只 2/2 物质球。',
  ae_controller: '轮次结束时生成 1 点普通法力，并随机生成一张不在初始牌组中的非 AE 卡牌到手牌。',
  ae_wireless: '随机生成一张不在初始牌组中的非 AE 卡牌到手牌。',
  dlb_tantrum: '使己方获得本轮尚未消耗的攻击标记。',
  dlb_ignore: '对敌方 Nexus 造成 3 点基础伤害；最终数值受双方电压与目标减伤影响。',
  dlb_chaos: '每个轮次结束时，使 DLB 强制保留攻击标记的间隔减少 1，最低为 3。',
  dlb_mood: '对一个随机可选中的敌方单位造成 1 点伤害。',
  dlb_nap: '使敌方当前普通法力减少 1，最低为 0。',
  dlb_scream: '使己方获得本轮尚未消耗的攻击标记，然后抽 1 张牌。',
};

for (const card of CARD_CATALOG) {
  if (card.kind === 'spell' && !card.spellSpeed) {
    card.spellSpeed = FAST_SPELLS.has(card.id) ? 'fast' : BURST_SPELLS.has(card.id) ? 'burst' : 'slow';
  }
  if (!card.rulesZh) {
    card.rulesZh =
      EXACT_RULES_ZH[card.id] ??
      (card.kind === 'unit'
        ? `召唤一个 ${card.attack ?? 0}/${card.health ?? 1} 单位。`
        : card.kind === 'structure'
          ? `召唤一个具有 ${card.health ?? 1} 点生命的结构。结构不能攻击或格挡。`
          : card.textZh ?? '无额外效果。');
  }
}

const byId = new Map(CARD_CATALOG.map((c) => [c.id, c]));

export function getCard(id: string): CardDef | undefined {
  return byId.get(id);
}

export function cardsByTheme(theme: string): CardDef[] {
  return CARD_CATALOG.filter((c) => c.theme === theme);
}

/** Off-deck AE generation pool: cards from other themes. */
export function aeOffDeckPool(deckCardIds: string[]): string[] {
  const inDeck = new Set(deckCardIds);
  return CARD_CATALOG.filter((c) => c.theme !== 'ae' && !inDeck.has(c.id)).map((c) => c.id);
}
