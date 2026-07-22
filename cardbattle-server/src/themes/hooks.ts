/** Theme mechanic notes — logic lives in battle/engine.ts + data/catalog.ts */
export const THEME_HOOKS = {
  vanilla: 'High-stat vanilla units',
  gt: 'Machines produce mana; capacitors bank overflow; overload explodes',
  thaum: 'Aspects; Ordo+Aer enables slot swap in block phase',
  forestry: 'Untargetable hives; bee spawn/merge; plugins & hybrid keywords',
  astral: 'Cheap spells; nexus DR% and reflect',
  avaritia: 'Singularity count → Eternal instant-kill blockers',
  ee: 'Accelerators reduce hive/structure cooldowns',
  genetics: 'Swarm / clone / low-cost units',
  ae: 'Generate off-deck cards from global pool',
  dlb: 'Periodic forced initiative; tantrum spells',
} as const;
