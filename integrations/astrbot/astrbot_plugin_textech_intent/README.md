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
