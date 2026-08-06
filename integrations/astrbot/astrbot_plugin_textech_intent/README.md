# TeXTech ↔ AstrBot intent handoff
#
# When WebAE and AstrBot share one QQ official bot AppID, enable this plugin and
# turn on WebAE admin → QQ bot →「启用意图唤起」. Matching rules must stay aligned.
#
# Install into AstrBot plugins dir, reload, then verify:
#   @bot tps          → only WebAE
#   @bot 讲个笑话      → only AstrBot
#   @bot webae 解释xx → only WebAE
#   @bot tt 搜一下xx  → only AstrBot
#   @bot tt生图 ...   → only AstrBot (compact prefix is supported)
#
# Since 1.2.0 the event also carries textech_route.original_text,
# textech_route.routed_text, textech_route.explicit, and textech_route.prefix.
# Downstream search/image plugins must use this metadata when they require tt
# after message_str has already been stripped.
#
# Since 1.2.1, ordinary HTTP(S) links, QQ Share/JSON/XML cards, explicit mobile
# `app`/`appmessage`/`ark`/`miniapp` rich cards, and Forward/Node messages are
# reserved for AstrBot link summarisation even when card text contains a WebAE
# keyword. Only bounded semantic navigation fields are inspected. Exact
# `webae ...` and configured slash commands such as `/tps ...` remain
# WebAE-owned; non-link routing is unchanged.
