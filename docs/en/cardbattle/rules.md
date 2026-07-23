# Card Battle Rules V2

## Opening mulligan

Each player draws four cards and confirms exactly one `mulligan`, replacing any number or keeping all four. Replacements are drawn from the current deck before returned cards are shuffled back, preventing an immediate redraw of the same card. Duplicate, out-of-range, or unfulfillable indices reject the entire action without changing either zone or confirmation state. The AI replaces opening cards costing 5 or more. After both players confirm, both take the normal round-one draw and enter main with five cards.

## Hand and deck limits

Hands hold at most ten cards. An excess deck draw or AE-generated card is revealed in the combat log and moved to discard. Attempting any draw while the deck is empty immediately loses the match; round, spell, and other draw effects share this rule.

## Rounds and priority

1. At round start, both players refill regular mana, heal every surviving unit to maximum health, and draw one card. The attack-token owner receives priority first.
2. Playing a unit, structure, slow spell, or fast spell uses one action and passes priority. A burst spell resolves immediately and keeps priority. Any non-pass action clears the consecutive-pass count.
3. One main-action pass gives the opponent another action window. Two consecutive main-action passes end the round. Response windows use a separate `responsePasses` counter and cannot end the round.
4. At round end, up to 3 unused regular mana becomes spell mana. Remaining mana may enter GT capacitor storage when available.
5. Every card spends regular mana first. Spells then use spell reserve, and GT capacitor storage pays last. Units and structures cannot spend spell mana.
6. A play validates its cost, bench capacity, and spell target before committing mana, hand, or board changes. Rejected plays consume nothing and do not pass priority. Units and structures enter discard only when they leave play, never immediately when played. Targeted spells must select the required legal unit, machine structure, stealth unit, or cooldown structure. The bench auto-packs left after units leave.

## Spell speeds, stack, and responses

| Speed | Legal timing | Uses stack | Priority after play |
|---|---|---|---|
| Slow `slow` | Empty `main` window only | Yes | Opponent |
| Fast `fast` | Empty `main`, `spell_response`, or `combat_response` | Yes | Opponent |
| Burst `burst` | `main` or either response window | No; resolves immediately | Caster keeps it |

1. Every spell declares its speed in the card catalog's `spellSpeed` field, and the frontend renders a Slow, Fast, or Burst badge.
2. A validated slow or fast spell leaves the hand for the public `spellStack`, which records `stackId`, `caster`, `cardId`, speed, and targets. It enters the caster's discard only after resolving or fizzling.
3. Adding a spell opens `spell_response` and gives response priority to the opponent. A responder may add a fast spell, resolve a burst spell immediately, or use `pass_priority`. Units, structures, and slow responses reject atomically.
4. Two consecutive response passes resolve the complete stack last-in-first-out. If an upper effect removes or invalidates a lower spell's target, that lower spell fizzles and still goes to discard.
5. After a main spell stack resolves, priority returns to the original caster's opponent. Response passes never increment `consecutivePasses`, so stack resolution cannot accidentally end the round.
6. The action protocol continues to use `play_card` and `pass_priority`. Public state adds `spell_response`, `combat_response`, `spellStack`, `responsePasses`, and `responseOriginPlayer`.

## Combat

The board uses a LoR-style dual row: **Bench** (up to 6 units/structures, auto-packed left) and **Battlefield** (combat lineup only). Units and structures play onto the bench without requiring a fixed empty slot. Only the player with an available attack token can start combat. Attackers are ordered left-to-right via `attackOrderIds` (instance ids); submitting an empty order cancels the declaration and preserves the token. The defender assigns at most one blocker to each attacker by dragging from their bench. Structures cannot block; stealth attackers require stealth blockers. Ordo + Aer allows one defensive bench-slot swap, while `pass_swap` skips it.

After blocks and the optional swap, the engine opens `combat_response` with the attacker holding response priority. Both sides may use fast or burst spells. Two response passes resolve the LIFO stack and only then combat damage. A declared blocker removed during this window leaves a ghost block, so the attacker does not redirect to Nexus. `combatAttacker` stays frozen and the attack token remains available throughout the response; the token and combat markers are cleared only after combat actually resolves. The match then returns to the alternating main window.

## Voltage

- Each defender voltage tier reduces incoming Nexus damage by 5%, with a 25% multiplier floor.
- Attacking below the defender's tier loses 35% output per tier gap, with a 15% floor.
- Voltage controls how many GTNH theme packs a deck may combine: ULV/LV starts at one, scaling to five at UV/UHV.

## Theme hooks

| Theme | Primary mechanic | Pace |
|---|---|---|
| Vanilla | Efficient raw stats. | Aggro |
| GregTech | Mana machines, capacitors, and overload. | Midrange |
| Thaumcraft | Aspects and Ordo + Aer repositioning. | Midrange |
| Forestry | Untargetable hives, bee production, and mutation. | Aggro |
| Astral Sorcery | Nexus reduction, reflection, and utility. | Midrange |
| Avaritia | Singularity progress and an Eternal finisher. | Control |
| Equivalent Exchange | Structure acceleration. | Control |
| Genetics | Cheap swarm and cloning. | Aggro |
| Applied Energistics | Generate cards from outside the deck. | Midrange |
| DLB | Disrupt normal attack-token scheduling. | Aggro |

Each theme has **40** catalog cards. Decks take cards from selected themes and pad to at least **40**. Spell resolution prefers the catalog `effect` payload; theme hooks keep special cases. Aggro themes bias cheap units and fast/burst spells; control themes bias cheap transition tools and expensive finishers.

## PvE route

The seeded route has two opening battles, a second branch, elite nodes, and a shared boss. Only IDs in `availableStageIds` may be entered. Post-battle choices add cards, grant a run power, or enqueue a voltage-tier reward. Boss completion also grants the `overclocked_nexus` board skin. Real GTNH item delivery remains a future bridge.

Starter equipment modifies the first non-structure unit in every encounter. Run powers may add Nexus health, starting spell mana, or extra first-unit stats; these bonuses stack with the selected starter equipment.

## Standalone persistence

`RunState` and authoritative `BattleState` snapshots use versioned JSON under `CARDBATTLE_DATA_DIR/sessions/` and are written through temporary files plus atomic replacement. Browser-local IDs are recovery indexes only. Pending item rewards always live in the backend ledger and remain accumulated when no MC server or single-player save bridge is configured.
