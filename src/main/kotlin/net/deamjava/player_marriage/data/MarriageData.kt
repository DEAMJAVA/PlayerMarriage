package net.deamjava.player_marriage.data

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.io.File
import java.time.Instant
import java.util.UUID

data class MarriageRecord(
    val player1Uuid: String,
    val player2Uuid: String,
    val player1Name: String,
    val player2Name: String,
    val marriedAt: Long = Instant.now().epochSecond,
    var homeX: Double? = null,
    var homeY: Double? = null,
    var homeZ: Double? = null,
    var homeDimension: String? = null
)

data class PendingProposal(
    val proposerUuid: String,
    val proposerName: String,
    val targetUuid: String,
    val timestamp: Long = Instant.now().epochSecond
)

object MarriageData {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private lateinit var dataFile: File
    private lateinit var server: MinecraftServer

    // uuid -> MarriageRecord (both players point to same record)
    private val marriages = mutableMapOf<String, MarriageRecord>()

    // uuid -> proposal
    val pendingProposals = mutableMapOf<String, PendingProposal>()

    // uuid -> true means PvP with partner is DISABLED (protected)
    private val pvpProtected = mutableSetOf<String>()

    // uuid -> true means private chat is toggled ON
    val chatToggled = mutableSetOf<String>()

    // uuid -> epoch seconds of last logout
    private val lastSeen = mutableMapOf<String, Long>()

    fun init(srv: MinecraftServer) {
        server = srv
        val configDir = FabricLoader.getInstance().configDir.resolve("player-marriage").toFile()
        configDir.mkdirs()
        dataFile = File(configDir, "marriages.json")
        load()
    }

    // ─── Persistence ────────────────────────────────────────────────────────

    private fun load() {
        if (!dataFile.exists()) return
        try {
            val json = dataFile.readText()
            val type = object : TypeToken<SavedData>() {}.type
            val saved: SavedData = gson.fromJson(json, type) ?: return
            marriages.clear()
            saved.marriages.forEach { record ->
                marriages[record.player1Uuid] = record
                marriages[record.player2Uuid] = record
            }
            pvpProtected.clear()
            pvpProtected.addAll(saved.pvpProtected)
            lastSeen.clear()
            lastSeen.putAll(saved.lastSeen)
        } catch (e: Exception) {
            net.deamjava.player_marriage.PlayerMarriage.logger.error("Failed to load marriage data", e)
        }
    }

    fun save() {
        try {
            dataFile.parentFile?.mkdirs()
            val uniqueRecords = marriages.values.distinctBy { it.player1Uuid + it.player2Uuid }
            val saved = SavedData(
                marriages = uniqueRecords,
                pvpProtected = pvpProtected.toList(),
                lastSeen = lastSeen
            )
            dataFile.writeText(gson.toJson(saved))
        } catch (e: Exception) {
            net.deamjava.player_marriage.PlayerMarriage.logger.error("Failed to save marriage data", e)
        }
    }

    // ─── Marriage ────────────────────────────────────────────────────────────

    fun isMarried(uuid: String): Boolean = marriages.containsKey(uuid)

    fun getPartnerUuid(uuid: String): String? {
        val record = marriages[uuid] ?: return null
        return if (record.player1Uuid == uuid) record.player2Uuid else record.player1Uuid
    }

    fun getPartnerName(uuid: String): String? {
        val record = marriages[uuid] ?: return null
        return if (record.player1Uuid == uuid) record.player2Name else record.player1Name
    }

    fun getRecord(uuid: String): MarriageRecord? = marriages[uuid]

    fun marry(p1: ServerPlayer, p2: ServerPlayer) {
        val record = MarriageRecord(
            player1Uuid = p1.stringUUID,
            player2Uuid = p2.stringUUID,
            player1Name = p1.name.string,
            player2Name = p2.name.string
        )
        marriages[p1.stringUUID] = record
        marriages[p2.stringUUID] = record
        // default: pvp protection on for both
        pvpProtected.add(p1.stringUUID)
        pvpProtected.add(p2.stringUUID)
        save()
    }

    fun divorce(uuid1: String, uuid2: String) {
        marriages.remove(uuid1)
        marriages.remove(uuid2)
        pvpProtected.remove(uuid1)
        pvpProtected.remove(uuid2)
        chatToggled.remove(uuid1)
        chatToggled.remove(uuid2)
        save()
    }

    // ─── Home ────────────────────────────────────────────────────────────────

    fun setHome(uuid: String, x: Double, y: Double, z: Double, dimension: String) {
        val record = marriages[uuid] ?: return
        record.homeX = x
        record.homeY = y
        record.homeZ = z
        record.homeDimension = dimension
        save()
    }

    fun deleteHome(uuid: String) {
        val record = marriages[uuid] ?: return
        record.homeX = null
        record.homeY = null
        record.homeZ = null
        record.homeDimension = null
        save()
    }

    fun hasHome(uuid: String): Boolean {
        val r = marriages[uuid] ?: return false
        return r.homeX != null
    }

    // ─── PvP ─────────────────────────────────────────────────────────────────

    fun isPvpProtected(uuid: String): Boolean = pvpProtected.contains(uuid)

    fun setPvpProtected(uuid: String, protected: Boolean) {
        if (protected) pvpProtected.add(uuid) else pvpProtected.remove(uuid)
        save()
    }

    // ─── Last Seen ───────────────────────────────────────────────────────────

    fun updateLastSeen(uuid: String) {
        lastSeen[uuid] = Instant.now().epochSecond
    }

    fun getLastSeen(uuid: String): Long? = lastSeen[uuid]

    // ─── Proposals ───────────────────────────────────────────────────────────

    fun addProposal(proposal: PendingProposal) {
        pendingProposals[proposal.targetUuid] = proposal
    }

    fun removeProposal(targetUuid: String) {
        pendingProposals.remove(targetUuid)
    }

    fun getProposal(targetUuid: String): PendingProposal? = pendingProposals[targetUuid]

    // ─── Player lookup helpers ───────────────────────────────────────────────

    fun getOnlinePlayer(uuid: String): ServerPlayer? =
        server.playerList.getPlayer(UUID.fromString(uuid))

    fun getServer(): MinecraftServer = server

    fun getAllCouples(): List<MarriageRecord> =
        marriages.values.distinctBy { it.player1Uuid + it.player2Uuid }
}

private data class SavedData(
    val marriages: List<MarriageRecord> = emptyList(),
    val pvpProtected: List<String> = emptyList(),
    val lastSeen: Map<String, Long> = emptyMap()
)
