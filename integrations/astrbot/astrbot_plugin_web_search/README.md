# TeXTech-style AstrBot web search plugin
#
# Paradigm matches WebAE: search first → inject into user prompt → then LLM.
# Engines: auto / tavily_keyless / duckduckgo / tavily / brave / serper / searxng
#
# Configure api_key / base_url in AstrBot WebUI plugin settings. Do not commit keys.
# Default trigger policy: `tt 搜索...` / `tt联网查...` only. Automatic group
# replies and ordinary chat never search unless both trigger guards are disabled.
