import logging
import mimetypes

from fastapi import APIRouter, HTTPException, Query
from fastapi.responses import FileResponse
from pydantic import BaseModel

from ..auth import require_perm
from ..services import image_gallery


router = APIRouter(prefix="/api/images", tags=["images"])
log = logging.getLogger("textech-console.images")
IMMUTABLE_IMAGE_CACHE = "private, max-age=31536000, immutable"
SHORT_IMAGE_CACHE = "private, max-age=300"


class FavoriteBody(BaseModel):
    favorite: bool


@router.get("")
def list_images(
    q: str | None = None,
    favorite: bool | None = None,
    operation: str | None = None,
    kind: str | None = None,
    backend: str | None = None,
    producer: str | None = None,
    has_prompt: bool | None = None,
    date_from: str | None = None,
    date_to: str | None = None,
    sort: str = "newest",
    page: int = Query(default=1, ge=1),
    limit: int = Query(default=24, ge=1, le=100),
    user=require_perm("images.view"),
):
    try:
        return image_gallery.list_images(
            user["id"],
            q=q,
            favorite=favorite,
            operation=operation,
            kind=kind,
            backend=backend,
            producer=producer,
            has_prompt=has_prompt,
            date_from=date_from,
            date_to=date_to,
            sort=sort,
            page=page,
            limit=limit,
        )
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc


@router.get("/facets")
def list_facets(user=require_perm("images.view")):
    return image_gallery.facets(user["id"])


@router.post("/rescan")
def rescan(_user=require_perm("images.manage")):
    return {"ok": True, **image_gallery.ensure_index(force=True)}


@router.get("/{image_id}")
def get_image(image_id: int, user=require_perm("images.view")):
    item = image_gallery.get_image(image_id, user["id"])
    if not item:
        raise HTTPException(404, "图片不存在或文件已移除")
    return item


def _cache_headers(requested_version: str | None, actual_version: str) -> dict[str, str]:
    cache_control = (
        IMMUTABLE_IMAGE_CACHE
        if requested_version and requested_version == actual_version
        else SHORT_IMAGE_CACHE
    )
    return {"Cache-Control": cache_control, "X-Content-Type-Options": "nosniff"}


@router.get("/{image_id}/file")
def get_image_file(
    image_id: int,
    v: str | None = Query(default=None, max_length=160),
    _user=require_perm("images.view"),
):
    info = image_gallery.image_file_info(image_id)
    if not info:
        raise HTTPException(404, "图片不存在或文件已移除")
    path, version = info
    media_type = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
    return FileResponse(
        path,
        media_type=media_type,
        headers=_cache_headers(v, version),
    )


@router.get("/{image_id}/thumbnail")
def get_image_thumbnail(
    image_id: int,
    v: str | None = Query(default=None, max_length=180),
    _user=require_perm("images.view"),
):
    try:
        info = image_gallery.thumbnail_file(image_id)
    except Exception as exc:
        log.warning("image thumbnail generation failed for id=%s: %s", image_id, exc)
        source = image_gallery.image_file_info(image_id)
        if not source:
            raise HTTPException(404, "图片不存在或文件已移除") from None
        path, _source_version = source
        media_type = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
        return FileResponse(
            path,
            media_type=media_type,
            headers={"Cache-Control": SHORT_IMAGE_CACHE, "X-Content-Type-Options": "nosniff"},
        )
    if not info:
        raise HTTPException(404, "图片不存在或文件已移除")
    path, version = info
    return FileResponse(
        path,
        media_type="image/webp",
        headers=_cache_headers(v, version),
    )


@router.put("/{image_id}/favorite")
def favorite_image(
    image_id: int,
    body: FavoriteBody,
    user=require_perm("images.favorite"),
):
    try:
        return image_gallery.set_favorite(image_id, user["id"], body.favorite)
    except KeyError:
        raise HTTPException(404, "图片不存在或文件已移除") from None
