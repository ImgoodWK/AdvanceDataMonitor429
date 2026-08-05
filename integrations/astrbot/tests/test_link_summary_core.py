from __future__ import annotations

import importlib
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch


PLUGIN_DIR = Path(__file__).resolve().parents[1] / "astrbot_plugin_link_summary"
PACKAGE_NAME = "_textech_link_summary_core_tests"
package = types.ModuleType(PACKAGE_NAME)
package.__path__ = [str(PLUGIN_DIR)]
package.__package__ = PACKAGE_NAME
sys.modules[PACKAGE_NAME] = package
core = importlib.import_module(f"{PACKAGE_NAME}.core")

BILI_API_HOST = core.BILI_API_HOST
BILI_HEADERS = core.BILI_HEADERS
DEFAULT_HEADERS = core.DEFAULT_HEADERS
ZHIHU_HEADERS = core.ZHIHU_HEADERS
ZHIHU_HOSTS = core.ZHIHU_HOSTS
BiliComment = core.BiliComment
BilibiliClient = core.BilibiliClient
FetchError = core.FetchError
SafeHttpFetcher = core.SafeHttpFetcher
UnsafeURL = core.UnsafeURL
ZhihuClient = core.ZhihuClient
_safe_cover_url = core._safe_cover_url
compact_page_data = core.compact_page_data
extract_bilibili_text_refs = core.extract_bilibili_text_refs
extract_page = core.extract_page
extract_urls = core.extract_urls
fallback_bili_comment_digest = core.fallback_bili_comment_digest
parse_bilibili_video_url = core.parse_bilibili_video_url
parse_zhihu_answer_url = core.parse_zhihu_answer_url
page_summary_rejection_reason = core.page_summary_rejection_reason
validate_url_network = core.validate_url_network
validate_url_syntax = core.validate_url_syntax


async def _public_addresses(*_args):
    return ["93.184.216.34"]


class _FakeCookies:
    def __init__(self):
        self.present = False
        self.clear_calls = 0

    def clear(self):
        self.present = False
        self.clear_calls += 1


class _FakeResponse:
    def __init__(
        self,
        status: int,
        *,
        headers: dict[str, str] | None = None,
        body: bytes = b"",
        chunks: list[bytes] | None = None,
        url: str | None = None,
        set_cookie: bool = False,
    ):
        self.status_code = status
        self.headers = headers or {}
        self._chunks = chunks if chunks is not None else [body]
        self.url = url
        self.set_cookie = set_cookie
        self.body_read = False
        self.client = None

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_args):
        if self.set_cookie and self.client is not None:
            self.client.cookies.present = True
        return None

    async def aiter_bytes(self):
        self.body_read = True
        for chunk in self._chunks:
            yield chunk


class _FakeClient:
    def __init__(self, responses: list[_FakeResponse], **_kwargs):
        self.responses = responses
        self.cookies = _FakeCookies()
        self.cookie_state_at_request: list[bool] = []

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_args):
        return None

    def stream(self, _method: str, url: str, **_kwargs):
        self.cookie_state_at_request.append(self.cookies.present)
        response = self.responses.pop(0)
        response.client = self
        response.url = response.url or url
        return response


def _fake_fetcher(responses: list[_FakeResponse], *, max_bytes: int = 1024 * 1024):
    clients: list[_FakeClient] = []

    def factory(**kwargs):
        client = _FakeClient(responses, **kwargs)
        clients.append(client)
        return client

    return (
        SafeHttpFetcher(
            client_factory=factory,
            resolver=_public_addresses,
            max_bytes=max_bytes,
        ),
        clients,
    )


class LinkSummaryURLTests(unittest.TestCase):
    def test_generic_page_headers_use_browser_ua_without_credentials(self):
        self.assertIn("Chrome/", DEFAULT_HEADERS["User-Agent"])
        self.assertIn("zh-CN", DEFAULT_HEADERS["Accept-Language"])
        self.assertNotIn("Cookie", DEFAULT_HEADERS)
        self.assertNotIn("Authorization", DEFAULT_HEADERS)

    def test_bilibili_headers_use_browser_ua_without_credentials(self):
        self.assertIn("Chrome/", BILI_HEADERS["User-Agent"])
        self.assertNotIn("Cookie", BILI_HEADERS)
        self.assertNotIn("Authorization", BILI_HEADERS)

    def test_zhihu_headers_request_json_without_credentials_or_referer(self):
        self.assertIn("Chrome/", ZHIHU_HEADERS["User-Agent"])
        self.assertIn("application/json", ZHIHU_HEADERS["Accept"])
        self.assertNotIn("Cookie", ZHIHU_HEADERS)
        self.assertNotIn("Authorization", ZHIHU_HEADERS)
        self.assertNotIn("Referer", ZHIHU_HEADERS)

    def test_extracts_only_requested_count_and_trims_chat_punctuation(self):
        text = "先看 https://example.com/a?q=1。再看（https://other.example/x）。"
        self.assertEqual(extract_urls(text, max_urls=1), ["https://example.com/a?q=1"])
        self.assertEqual(
            extract_urls(text, max_urls=2),
            ["https://example.com/a?q=1", "https://other.example/x"],
        )

    def test_normalizes_only_standalone_bilibili_text_references(self):
        self.assertEqual(
            extract_bilibili_text_refs("看看 bv1xx411c7mD，再看 AV2", max_urls=2),
            [
                "https://www.bilibili.com/video/BV1xx411c7mD",
                "https://www.bilibili.com/video/av2",
            ],
        )
        self.assertEqual(
            extract_bilibili_text_refs(
                "https://www.bilibili.com/video/BV1xx411c7mD?from=qq"
            ),
            [],
        )
        for value in (
            "prefixBV1xx411c7mD",
            "BV1xx411c7mDtail",
            "avatar123",
            "BV-too-short",
        ):
            with self.subTest(value=value):
                self.assertEqual(extract_bilibili_text_refs(value), [])

    def test_rejects_unsafe_schemes_userinfo_ports_and_literal_targets(self):
        values = (
            "file:///etc/passwd",
            "javascript:alert(1)",
            "https://user:pass@example.com/",
            "https://example.com:8080/",
            "http://127.0.0.1/",
            "http://[::1]/",
            "http://[::ffff:127.0.0.1]/",
            "http://169.254.169.254/latest/meta-data/",
        )
        for value in values:
            with self.subTest(value=value), self.assertRaises(UnsafeURL):
                validate_url_syntax(value)

    def test_normalizes_idna_host_and_removes_fragment(self):
        normalized = validate_url_syntax("HTTPS://例子.测试/a?q=1#fragment")
        self.assertEqual(
            normalized,
            "https://xn--fsqu00a.xn--0zwm56d/a?q=1",
        )

    def test_bilibili_video_hosts_are_exact(self):
        self.assertEqual(
            parse_bilibili_video_url(
                "https://www.bilibili.com/video/BV1xx411c7mD"
            ),
            ("bvid", "BV1xx411c7mD"),
        )
        self.assertEqual(
            parse_bilibili_video_url(
                "https://m.bilibili.com/video/bv1xx411c7mD"
            ),
            ("bvid", "BV1xx411c7mD"),
        )
        self.assertEqual(
            parse_bilibili_video_url("https://www.bilibili.com/video/av2"),
            ("aid", "2"),
        )
        for value in (
            "https://bilibili.com/video/BV1xx411c7mD",
            "https://bilibili.com.evil.example/video/BV1xx411c7mD",
            "https://www.bilibili.com/read/cv1",
            "https://b23.tv/BV1xx411c7mD",
        ):
            with self.subTest(value=value):
                self.assertIsNone(parse_bilibili_video_url(value))

    def test_cover_host_allowlist_is_exact_and_forces_https(self):
        self.assertEqual(
            _safe_cover_url("http://i0.hdslb.com/bfs/archive/a.jpg"),
            "https://i0.hdslb.com/bfs/archive/a.jpg",
        )
        for value in (
            "https://hdslb.com/a.jpg",
            "https://i3.hdslb.com/a.jpg",
            "https://i0.hdslb.com.evil.example/a.jpg",
            "https://example.com/a.jpg",
        ):
            with self.subTest(value=value):
                self.assertEqual(_safe_cover_url(value), "")

    def test_zhihu_answer_url_requires_exact_host_and_numeric_answer_path(self):
        self.assertEqual(
            parse_zhihu_answer_url(
                "https://www.zhihu.com/question/123/answer/456/?utm_source=qq"
            ),
            "456",
        )
        for value in (
            "https://zhihu.com/question/123/answer/456",
            "https://www.zhihu.com.evil.example/question/123/answer/456",
            "https://user@www.zhihu.com/question/123/answer/456",
            "https://www.zhihu.com:8443/question/123/answer/456",
            "https://www.zhihu.com/question/not-a-number/answer/456",
            "https://www.zhihu.com/question/123/answer/not-a-number",
            "https://www.zhihu.com/question/123",
        ):
            with self.subTest(value=value):
                self.assertIsNone(parse_zhihu_answer_url(value))


class LinkSummaryNetworkTests(unittest.IsolatedAsyncioTestCase):
    async def test_checks_every_dns_answer_including_ipv4_mapped_ipv6(self):
        async def resolver(_host: str, _port: int):
            return ["93.184.216.34", "::ffff:10.0.0.2"]

        with self.assertRaises(UnsafeURL):
            await validate_url_network("https://example.com/", resolver=resolver)

    async def test_redirect_target_is_revalidated_before_request(self):
        response = _FakeResponse(
            302,
            headers={"location": "http://127.0.0.1/"},
        )
        fetcher, _clients = _fake_fetcher([response])
        with self.assertRaises(UnsafeURL):
            await fetcher.fetch("https://example.com/")

    async def test_allowlisted_redirect_cannot_escape_to_another_public_host(self):
        response = _FakeResponse(
            302,
            headers={"location": "https://evil.example/video/BV1xx411c7mD"},
        )
        fetcher, _clients = _fake_fetcher([response])
        with self.assertRaises(UnsafeURL):
            await fetcher.resolve(
                "https://b23.tv/BV1xx411c7mD",
                allowed_hosts={"b23.tv", "www.bilibili.com"},
            )

    async def test_missing_or_binary_content_type_is_rejected_before_body_read(self):
        for headers in ({}, {"content-type": "video/mp4"}):
            response = _FakeResponse(200, headers=headers, body=b"binary")
            fetcher, _clients = _fake_fetcher([response])
            with self.subTest(headers=headers), self.assertRaises(FetchError):
                await fetcher.fetch("https://example.com/file")
            self.assertFalse(response.body_read)

    async def test_declared_and_streamed_oversized_responses_are_rejected(self):
        declared = _FakeResponse(
            200,
            headers={"content-type": "text/html", "content-length": "20000"},
            body=b"small",
        )
        fetcher, _clients = _fake_fetcher([declared], max_bytes=16 * 1024)
        with self.assertRaises(FetchError):
            await fetcher.fetch("https://example.com/large")
        self.assertFalse(declared.body_read)

        streamed = _FakeResponse(
            200,
            headers={"content-type": "text/html"},
            chunks=[b"x" * 9000, b"x" * 9000],
        )
        fetcher, _clients = _fake_fetcher([streamed], max_bytes=16 * 1024)
        with self.assertRaises(FetchError):
            await fetcher.fetch("https://example.com/chunked")
        self.assertTrue(streamed.body_read)

    async def test_redirect_hops_do_not_replay_response_cookies(self):
        responses = [
            _FakeResponse(
                302,
                headers={"location": "https://example.com/final"},
                set_cookie=True,
            ),
            _FakeResponse(
                200,
                headers={"content-type": "text/plain"},
                body=b"ok",
            ),
        ]
        fetcher, clients = _fake_fetcher(responses)
        result = await fetcher.fetch("https://example.com/start")
        self.assertEqual(result.content, b"ok")
        self.assertEqual(clients[0].cookie_state_at_request, [False, False])
        self.assertEqual(clients[0].cookies.clear_calls, 2)

    async def test_transient_public_status_retries_once_with_a_fresh_client(self):
        responses = [
            _FakeResponse(503, headers={"content-type": "text/plain"}),
            _FakeResponse(
                200,
                headers={"content-type": "text/plain"},
                body=b"recovered",
            ),
        ]
        fetcher, clients = _fake_fetcher(responses)
        with patch.object(core.asyncio, "sleep", new_callable=AsyncMock) as sleep:
            result = await fetcher.fetch("https://example.com/transient")

        self.assertEqual(result.content, b"recovered")
        self.assertEqual(len(clients), 2)
        self.assertEqual([call.args[0] for call in sleep.await_args_list], [0.35])
        self.assertEqual(clients[0].cookie_state_at_request, [False])
        self.assertEqual(clients[1].cookie_state_at_request, [False])

    async def test_permanent_status_and_invalid_mime_are_not_retried(self):
        for response in (
            _FakeResponse(403, headers={"content-type": "text/plain"}),
            _FakeResponse(200, headers={"content-type": "video/mp4"}, body=b"x"),
        ):
            fetcher, clients = _fake_fetcher([response])
            with self.subTest(status=response.status_code), patch.object(
                core.asyncio,
                "sleep",
                new_callable=AsyncMock,
            ) as sleep:
                with self.assertRaises(FetchError):
                    await fetcher.fetch("https://example.com/permanent")
                sleep.assert_not_awaited()
                self.assertEqual(len(clients), 1)

    async def test_short_link_resolution_retries_only_one_transient_response(self):
        responses = [
            _FakeResponse(429, headers={"content-type": "text/plain"}),
            _FakeResponse(
                302,
                headers={
                    "location": "https://www.bilibili.com/video/BV1xx411c7mD"
                },
            ),
            _FakeResponse(200, headers={"content-type": "text/html"}),
        ]
        fetcher, clients = _fake_fetcher(responses)
        with patch.object(core.asyncio, "sleep", new_callable=AsyncMock) as sleep:
            result = await fetcher.resolve(
                "https://b23.tv/example",
                allowed_hosts={"b23.tv", "www.bilibili.com"},
            )

        self.assertEqual(result, "https://www.bilibili.com/video/BV1xx411c7mD")
        self.assertEqual(len(clients), 2)
        self.assertEqual([call.args[0] for call in sleep.await_args_list], [0.35])


class LinkSummaryPageTests(unittest.TestCase):
    def test_page_summary_quality_rejects_empty_and_verbatim_long_output(self):
        source = " ".join(
            f"第{index}段原始材料用于说明一个不同的事实和论据。"
            for index in range(60)
        )
        self.assertEqual(page_summary_rejection_reason("", source), "empty")
        self.assertEqual(
            page_summary_rejection_reason(source[:900], source),
            "verbatim",
        )

    def test_page_summary_quality_rejects_excerpts_joined_under_new_headings(self):
        source = " ".join(
            f"原文段落{index}包含需要被模型理解后改写的独立内容和详细论证。"
            for index in range(80)
        )
        copied_sections = (
            f"主题：{source[100:650]}\n"
            f"关键要点：{source[900:1500]}\n"
            f"结论：{source[1800:2300]}"
        )
        self.assertEqual(
            page_summary_rejection_reason(copied_sections, source),
            "near_copy",
        )

    def test_page_summary_quality_requires_real_compression_for_long_pages(self):
        source = "原始页面材料包含事实依据和论证过程" * 120
        long_rewording = "重新组织后的文字仍然保留了过多细节与展开说明" * 75
        self.assertEqual(
            page_summary_rejection_reason(long_rewording, source),
            "insufficient_compression",
        )

    def test_page_summary_quality_accepts_a_concise_rewritten_digest(self):
        source = " ".join(
            f"材料第{index}部分展开背景、例证、推理和补充限制。"
            for index in range(80)
        )
        summary = (
            "主题：文章讨论如何在真诚沟通中建立边界。"
            "关键要点：关系需要对等反馈，也需要允许不同节奏。"
            "结论或争议：真诚本身并非问题，表达方式与接收能力同样重要。"
        )
        self.assertIsNone(page_summary_rejection_reason(summary, source))

    def test_parser_discards_noise_and_extracts_metadata(self):
        body = b"""<html><head><title>News</title>
        <meta property='og:description' content='desc'></head><body>
        <nav>menu</nav><article><h1>Heading</h1><p>Hello world.</p>
        <script>ignore()</script></article></body></html>"""
        page = extract_page(
            "https://example.com/",
            body,
            content_type="text/html; charset=utf-8",
        )
        self.assertEqual(page.title, "News")
        self.assertEqual(page.description, "desc")
        self.assertIn("Hello world.", page.text)
        self.assertNotIn("ignore", page.text)
        self.assertNotIn("menu", page.text)

    def test_parser_filters_hidden_role_and_class_noise_without_dropping_content(self):
        body = """<html><head><title>干净标题</title></head><body>
        <header>站点头部</header><div role='navigation'>角色导航</div>
        <div hidden>隐藏内容</div><div aria-hidden='true'>无障碍隐藏内容</div>
        <div style='display: none !important'>样式隐藏内容</div>
        <main><div class='article-content'><p>第一段正文，应该被保留下来。</p>
        <p>第二段正文，也应该被保留下来。</p></div>
        <div class='comment-list'>评论区噪音</div>
        <div id='related-recommendations'>相关推荐噪音</div>
        <button>按钮噪音</button><iframe>框架噪音</iframe></main></body></html>"""
        page = extract_page(
            "https://example.com/article",
            body.encode("utf-8"),
            content_type="text/html; charset=utf-8",
        )
        self.assertIn("第一段正文", page.text)
        self.assertIn("第二段正文", page.text)
        for noise in (
            "站点头部",
            "角色导航",
            "隐藏内容",
            "无障碍隐藏内容",
            "样式隐藏内容",
            "评论区噪音",
            "相关推荐噪音",
            "按钮噪音",
            "框架噪音",
        ):
            with self.subTest(noise=noise):
                self.assertNotIn(noise, page.text)

    def test_void_noise_elements_do_not_hide_following_dom_content(self):
        body = """<body><main><p>输入框之前的正文。</p>
        <input type='hidden' value='transport-only'>
        <p>输入框之后的正文。</p><embed src='https://cdn.example/unused'>
        <p>嵌入元素之后的正文。</p><br><p>最后一段正文。</p></main></body>"""
        page = extract_page(
            "https://example.com/article",
            body.encode("utf-8"),
            content_type="text/html; charset=utf-8",
        )
        self.assertIn("输入框之前的正文", page.text)
        self.assertIn("输入框之后的正文", page.text)
        self.assertIn("嵌入元素之后的正文", page.text)
        self.assertIn("最后一段正文", page.text)
        self.assertNotIn("transport-only", page.text)

    def test_short_article_falls_back_to_clean_body_and_repeated_blocks_are_deduplicated(self):
        repeated = "这是一段长度足够的重复正文，用来验证模板或重复节点不会成倍放大模型输入。"
        body = f"""<body><p>正文前言，位于短 article 之外。</p>
        <article><p>短主体</p></article><p>{repeated}</p><p>{repeated}</p></body>"""
        page = extract_page("https://example.com/", body.encode("utf-8"), limit=1000)
        self.assertIn("正文前言", page.text)
        self.assertIn("短主体", page.text)
        self.assertEqual(page.text.count(repeated), 1)

    def test_page_and_compact_llm_budgets_are_hard_limits(self):
        page = extract_page(
            "https://example.com/",
            ("<body><p>" + ("正文" * 2000) + "</p></body>").encode("utf-8"),
            limit=300,
        )
        self.assertLessEqual(len(page.text), 300)
        compact = compact_page_data(
            core.PageData(
                url=page.url,
                title="标题" * 300,
                description="描述" * 1000,
                text="正文" * 5000,
            ),
            limit=800,
        )
        self.assertLessEqual(
            len(compact.title) + len(compact.description) + len(compact.text),
            800,
        )
        self.assertTrue(compact.truncated)


class _FakeZhihuFetcher:
    def __init__(
        self,
        payload: dict,
        *,
        failures: int = 0,
        events: list[tuple[str, float | None]] | None = None,
    ):
        self.payload = payload
        self.failures = failures
        self.calls: list[dict] = []
        self.events = events

    async def json(self, url: str, *, headers=None, allowed_hosts=None):
        self.calls.append(
            {"url": url, "headers": headers, "allowed_hosts": allowed_hosts}
        )
        if self.events is not None:
            self.events.append(("fetch", None))
        if len(self.calls) <= self.failures:
            raise FetchError("transient public endpoint failure")
        return self.payload


class ZhihuClientTests(unittest.IsolatedAsyncioTestCase):
    async def test_retries_one_transient_public_answer_fetch(self):
        fetcher = _FakeZhihuFetcher(
            {
                "content": "<p>可供模型提炼的公开回答正文。</p>",
                "excerpt": "公开摘录",
                "question": {"title": "问题标题"},
            },
            failures=1,
        )
        with patch.object(core.asyncio, "sleep", new_callable=AsyncMock) as sleep:
            page = await ZhihuClient(fetcher).fetch_answer(
                "https://www.zhihu.com/question/123/answer/456"
            )
        self.assertEqual(len(fetcher.calls), 2)
        self.assertIn("公开回答正文", page.text)
        self.assertEqual(
            [call.args[0] for call in sleep.await_args_list],
            [0.35],
        )

    async def test_retries_after_two_failed_public_answer_fetches(self):
        events: list[tuple[str, float | None]] = []
        fetcher = _FakeZhihuFetcher(
            {
                "content": "<p>三次请求后成功的公开回答正文。</p>",
                "excerpt": "公开摘录",
                "question": {"title": "问题标题"},
            },
            failures=2,
            events=events,
        )

        async def record_sleep(delay: float):
            events.append(("sleep", delay))

        with patch.object(core.asyncio, "sleep", new_callable=AsyncMock) as sleep:
            sleep.side_effect = record_sleep
            page = await ZhihuClient(fetcher).fetch_answer(
                "https://www.zhihu.com/question/123/answer/456"
            )
        self.assertEqual(len(fetcher.calls), 3)
        self.assertIn("三次请求后成功的公开回答正文", page.text)
        self.assertEqual(
            [call.args[0] for call in sleep.await_args_list],
            [0.35, 1.0],
        )
        self.assertEqual(
            events,
            [
                ("fetch", None),
                ("sleep", 0.35),
                ("fetch", None),
                ("sleep", 1.0),
                ("fetch", None),
            ],
        )

    async def test_stops_after_three_failed_public_answer_fetches(self):
        events: list[tuple[str, float | None]] = []
        fetcher = _FakeZhihuFetcher({}, failures=3, events=events)

        async def record_sleep(delay: float):
            events.append(("sleep", delay))

        with patch.object(core.asyncio, "sleep", new_callable=AsyncMock) as sleep:
            sleep.side_effect = record_sleep
            with self.assertRaises(FetchError):
                await ZhihuClient(fetcher).fetch_answer(
                    "https://www.zhihu.com/question/123/answer/456"
                )
        self.assertEqual(len(fetcher.calls), 3)
        self.assertEqual(
            [call.args[0] for call in sleep.await_args_list],
            [0.35, 1.0],
        )
        self.assertEqual(
            events,
            [
                ("fetch", None),
                ("sleep", 0.35),
                ("fetch", None),
                ("sleep", 1.0),
                ("fetch", None),
            ],
        )

    async def test_fetches_only_public_answer_content_and_discards_identity_fields(self):
        fetcher = _FakeZhihuFetcher(
            {
                "content": "<div><p>回答正文第一段。</p><div class='comment-list'>噪音评论</div><p>回答正文第二段。</p></div>",
                "excerpt": "公开摘录",
                "question": {"title": "问题标题"},
                "content_need_truncated": True,
                "author": {
                    "name": "不得进入结果的作者",
                    "id": "fake-uid",
                    "avatar_url": "https://example.com/avatar.jpg",
                },
            }
        )
        page = await ZhihuClient(fetcher).fetch_answer(
            "https://www.zhihu.com/question/123/answer/456?from=qq",
            text_limit=1000,
        )
        self.assertEqual(page.title, "问题标题")
        self.assertEqual(page.description, "公开摘录")
        self.assertIn("回答正文第一段", page.text)
        self.assertIn("回答正文第二段", page.text)
        self.assertNotIn("噪音评论", page.text)
        self.assertTrue(page.truncated)
        self.assertFalse(hasattr(page, "author"))
        self.assertNotIn("不得进入结果的作者", repr(page))
        self.assertNotIn("fake-uid", repr(page))
        self.assertEqual(fetcher.calls[0]["allowed_hosts"], ZHIHU_HOSTS)
        self.assertIn("/api/v4/answers/456?", fetcher.calls[0]["url"])
        self.assertEqual(fetcher.calls[0]["headers"], ZHIHU_HEADERS)

    async def test_empty_answer_content_and_excerpt_fail_safely(self):
        fetcher = _FakeZhihuFetcher(
            {"content": "", "excerpt": "", "question": {"title": "只有标题"}}
        )
        with self.assertRaises(FetchError):
            await ZhihuClient(fetcher).fetch_answer(
                "https://www.zhihu.com/question/123/answer/456"
            )


class _FakeJSONFetcher:
    def __init__(
        self,
        *,
        comment_code: int = 0,
        detail_code: int = 0,
    ):
        self.comment_code = comment_code
        self.detail_code = detail_code
        self.calls: list[dict] = []

    async def json(self, url: str, *, headers=None, allowed_hosts=None):
        self.calls.append(
            {
                "url": url,
                "headers": headers,
                "allowed_hosts": allowed_hosts,
            }
        )
        if "web-interface/view" in url:
            if self.detail_code != 0:
                return {"code": self.detail_code, "data": None}
            return {
                "code": 0,
                "data": {
                    "bvid": "BV1xx411c7mD",
                    "aid": 2,
                    "pic": "https://i0.hdslb.com/bfs/archive/a.jpg",
                    "title": "测试标题",
                    "desc": "测试简介",
                    "pubdate": 1700000000,
                    "owner": {"name": "测试 UP"},
                    "duration": 61,
                    "stat": {"view": 10000, "reply": 3, "like": 5},
                },
            }
        if self.comment_code != 0:
            return {"code": self.comment_code, "data": None}
        return {
            "code": 0,
            "data": {
                "replies": [
                    {
                        "content": {"message": "观点 A"},
                        "like": 30,
                        "member": {"uname": "不得进入结果的昵称"},
                    },
                    {
                        "content": {"message": "观点 A"},
                        "like": 20,
                        "member": {"uname": "另一个昵称"},
                    },
                    {
                        "content": {"message": "观点 B"},
                        "like": 10,
                        "member": {"uname": "第三个昵称"},
                    },
                ]
            },
        }


class BilibiliClientTests(unittest.IsolatedAsyncioTestCase):
    async def test_fetches_metadata_uses_utc8_and_deduplicates_anonymous_comments(self):
        fetcher = _FakeJSONFetcher()
        video = await BilibiliClient(fetcher).fetch_video(
            "https://www.bilibili.com/video/BV1xx411c7mD",
            max_comments=2,
        )
        self.assertEqual(video.title, "测试标题")
        self.assertEqual(video.stats["view"], 10000)
        self.assertEqual(video.published_at, "2023-11-15 06:13")
        self.assertEqual([item.message for item in video.comments], ["观点 A", "观点 B"])
        self.assertFalse(hasattr(video.comments[0], "username"))
        self.assertNotIn("昵称", repr(video.comments))
        self.assertEqual(len(fetcher.calls), 2)
        self.assertEqual(fetcher.calls[0]["allowed_hosts"], {BILI_API_HOST})
        self.assertEqual(fetcher.calls[1]["allowed_hosts"], {BILI_API_HOST})

    async def test_comment_failure_is_soft_but_detail_failure_is_hard(self):
        video = await BilibiliClient(
            _FakeJSONFetcher(comment_code=-352)
        ).fetch_video("https://www.bilibili.com/video/BV1xx411c7mD")
        self.assertEqual(video.title, "测试标题")
        self.assertFalse(video.comments)
        self.assertTrue(video.comments_error)

        with self.assertRaises(FetchError):
            await BilibiliClient(
                _FakeJSONFetcher(detail_code=-404)
            ).fetch_video("https://www.bilibili.com/video/BV1xx411c7mD")

    async def test_zero_comment_limit_skips_comment_request(self):
        fetcher = _FakeJSONFetcher()
        video = await BilibiliClient(fetcher).fetch_video(
            "https://www.bilibili.com/video/BV1xx411c7mD",
            max_comments=0,
        )
        self.assertFalse(video.comments)
        self.assertEqual(len(fetcher.calls), 1)

    def test_fallback_digest_contains_no_identity_field(self):
        digest = fallback_bili_comment_digest([BiliComment("很有意思", 12)])
        self.assertIn("很有意思", digest)
        self.assertIn("赞 12", digest)
        self.assertNotIn("@", digest)


if __name__ == "__main__":
    unittest.main()
