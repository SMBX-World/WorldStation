package ink.chyk.worldstation.controller

import ink.chyk.worldstation.dto.*
import ink.chyk.worldstation.enum.GameVersion
import ink.chyk.worldstation.repository.WorldMapRepository
import ink.chyk.worldstation.service.OneDriveService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/worldmaps")
class WorldMapController(
    private val repository: WorldMapRepository,
    private val onedrive: OneDriveService
) {
    @PostMapping
    fun newWorldMap(@RequestBody worldMapDTO: WorldMapDTO): ApiResponseDTO<WorldMapDTO> {
        return ApiResponseDTO(data = repository.newWorldMap(worldMapDTO))
    }

    @PutMapping
    fun updateWorldMap(
        @RequestBody worldMapDTO: WorldMapDTO,
        @AuthenticationPrincipal principal: OAuth2User
    ): ApiResponseDTO<Boolean> {
        // 比对上传者
        if (worldMapDTO.uploader != principal.getAttribute<Int>("id")) {
            return ApiResponseDTO(code = 403, message = "您没有权限修改该地图信息")
        }

        val successOrNot = repository.updateWorldMap(worldMapDTO)
        if (successOrNot) {
            return ApiResponseDTO(message = "更新地图信息成功", data = true)
        } else {
            return ApiResponseDTO(code = 404, message = "请求的地图不存在")
        }
    }

    @GetMapping("/worldmap/{id}")
    fun getWorldMapById(@PathVariable id: Int): ApiResponseDTO<WorldMapDTO> {
        val worldMap = repository.getWorldMapById(id)
        return if (worldMap != null) {
            ApiResponseDTO(data = worldMap)
        } else {
            ApiResponseDTO(code = 404, message = "请求的地图不存在")
        }
    }

    @GetMapping()
    fun searchWorldMaps(
        // 竟然都要手工写 required=false，那很不咳特灵了
        @RequestParam(required = false) query: String? = null,
        @RequestParam(required = false) pageSize: Int = 20,
        @RequestParam(required = false) page: Int = 0,
        @RequestParam(required = false) version: String? = null,
        @RequestParam(required = false) uploader: Int? = null,
        @RequestParam(required = false) sort: String? = null
    ): ApiResponseDTO<List<WorldMapDTO>> {
        // 处理游戏版本号
        val version = if (version != null) {
            try {
                GameVersion.valueOf(version)
            } catch (_: IllegalArgumentException) {
                return@searchWorldMaps ApiResponseDTO<List<WorldMapDTO>>(
                    code = 400,
                    message = "无效的游戏版本号: $version"
                )
            }
        } else {
            null
        }
        // 查询
        val worldMaps = repository.queryWorldMaps(
            query = query,
            pageSize = pageSize,
            pageNumber = page,
            version = version,
            uploader = uploader,
            sort = sort
        )
        return ApiResponseDTO(data = worldMaps)
    }

    @DeleteMapping("/worldmap/{id}")
    fun deleteWorldMap(
        @PathVariable id: Int,
        @AuthenticationPrincipal principal: OAuth2User
    ): ApiResponseDTO<Boolean> {
        val worldMapUploader = repository.getWorldMapById(id)?.uploader
            ?: return ApiResponseDTO(code = 404, message = "请求的地图不存在")
        // 检查地图是否属于当前用户
        if (worldMapUploader != principal.getAttribute<Int>("id")) {
            return ApiResponseDTO(code = 403, message = "您没有权限删除该地图")
        }
        val url = repository.getWorldMapById(id)?.downloadUrl
            ?: return ApiResponseDTO(code = 404, message = "请求的地图不存在")
        val successOrNot = repository.deleteWorldMapById(id)
        val deleteOrNot = onedrive.tryRemoveByUrl(url)
        return if (successOrNot && deleteOrNot) {
            ApiResponseDTO(message = "删除成功", data = true)
        } else {
            ApiResponseDTO(code = 500, message = "删除失败", data = false)
        }
    }
}
