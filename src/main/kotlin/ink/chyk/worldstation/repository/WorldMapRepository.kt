package ink.chyk.worldstation.repository

import ink.chyk.worldstation.dto.WorldMapDTO
import ink.chyk.worldstation.entity.WorldMap
import ink.chyk.worldstation.enum.GameVersion
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository

@Repository
class WorldMapRepository {
    fun newWorldMap(worldMapDto: WorldMapDTO): WorldMapDTO = transaction {
        val id = WorldMap.insert {
            it[title] = worldMapDto.title
            it[title_lower] = worldMapDto.title.lowercase()
            it[author] = worldMapDto.author
            it[uploader] = worldMapDto.uploader
            it[gameVersion] = worldMapDto.gameVersion
            it[downloadProvider] = worldMapDto.downloadProvider
            it[downloadUrl] = worldMapDto.downloadUrl
        } get WorldMap.id

        worldMapDto.copy(id = id)
    }

    fun updateWorldMap(worldMapDto: WorldMapDTO): Boolean = transaction {
        // 请求参数错误则直接返回 false
        if (worldMapDto.id == null) return@transaction false
        WorldMap.update({ WorldMap.id eq worldMapDto.id }) {
            it[title] = worldMapDto.title
            it[title_lower] = worldMapDto.title.lowercase()
            it[author] = worldMapDto.author
            it[uploader] = worldMapDto.uploader
            it[gameVersion] = worldMapDto.gameVersion
            it[downloadProvider] = worldMapDto.downloadProvider
            it[downloadUrl] = worldMapDto.downloadUrl
        } > 0  // 更新的行数大于 0 则表示更新成功
    }

    fun getWorldMapById(id: Int): WorldMapDTO? = transaction {
        WorldMap.selectAll().where { WorldMap.id eq id }.firstOrNull()?.let {
            WorldMapDTO.fromEntity(it)
        }
    }

    fun queryWorldMaps(
        query: String? = null,
        pageSize: Int = 20,
        pageNumber: Int = 0,
        uploader: Int? = null,
        version: GameVersion? = null,
        sort: String? = null
    ) = transaction {
        WorldMap.selectAll().apply {
            /*
            if (!versions.isNullOrEmpty()) {
                where { WorldMap.gameVersion inList versions }
            }
             */
            version?.let {
                where { WorldMap.gameVersion eq it }
            }
            uploader?.let {
                where { WorldMap.uploader eq it }
            }
            if (query != null && query.isNotBlank()) {
                // title_lower 列具有 trgm 索引，因此 like 的效率不会很差
                val likePattern = query.lowercase()
                    .replace("%", "\\%")
                    .replace("_", "\\_")
                    // 一个或多个空格替换为%
                    .replace(Regex("\\s+"), "%")
                    // 处理首尾空格
                    .trim('%')
                    .let { "%$it%" }
                where { WorldMap.title_lower like likePattern }
            }
            limit(pageSize)
            if (pageNumber > 0) {
                offset(pageNumber * pageSize.toLong())
            }
            val sortOrder = if (sort == "title") {
                WorldMap.title to SortOrder.ASC
            } else {
                WorldMap.id to SortOrder.DESC
            }
            orderBy(sortOrder)
        }.map {
            WorldMapDTO.fromEntity(it)
        }
    }

    fun deleteWorldMapById(id: Int): Boolean = transaction {
        WorldMap.deleteWhere { WorldMap.id eq id } > 0
    }
}
