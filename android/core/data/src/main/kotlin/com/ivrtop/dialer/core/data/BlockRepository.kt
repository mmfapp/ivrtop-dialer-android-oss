package com.ivrtop.dialer.core.data

import com.ivrtop.dialer.core.common.Clock
import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.database.BlockDao
import com.ivrtop.dialer.core.database.BlockRuleEntity
import com.ivrtop.dialer.core.models.BlockKind
import com.ivrtop.dialer.core.models.BlockListType
import com.ivrtop.dialer.core.models.BlockRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockRepository @Inject constructor(private val dao: BlockDao, private val clock: Clock) {

    val rules: Flow<List<BlockRule>> = dao.observeAll().map { l -> l.map { it.toModel() } }

    suspend fun add(pattern: String, kind: BlockKind, list: BlockListType = BlockListType.DENY, label: String? = null, syncToServer: Boolean = false) {
        val p = if (kind == BlockKind.ANONYMOUS) "anonymous" else PhoneNumbers.stripFormatting(pattern)
        if (p.isEmpty()) return
        dao.insert(BlockRuleEntity(pattern = p, kind = kind.name, list = list.name, label = label, accountId = null, syncToServer = syncToServer, createdAt = clock.now()))
    }

    suspend fun remove(id: Long) = dao.delete(id)

    suspend fun all(): List<BlockRule> = dao.getAll().map { it.toModel() }

    private fun BlockRuleEntity.toModel() = BlockRule(id, pattern, BlockKind.valueOf(kind), BlockListType.valueOf(list), label, accountId, syncToServer, createdAt)

    companion object {
        private val ANON = setOf("anonymous", "restricted", "unknown", "private", "unavailable", "")

        fun isAnonymous(number: String) = number.trim().lowercase() in ANON

        /** true = blocked. Allow-list entries override deny rules. */
        fun evaluate(number: String, rules: List<BlockRule>): Boolean {
            val allow = rules.filter { it.list == BlockListType.ALLOW }
            if (allow.any { matches(number, it) }) return false
            return rules.filter { it.list == BlockListType.DENY }.any { matches(number, it) }
        }

        fun matches(number: String, rule: BlockRule): Boolean = when (rule.kind) {
            BlockKind.ANONYMOUS -> isAnonymous(number)
            BlockKind.EXACT -> PhoneNumbers.sameNumber(number, rule.pattern) || number == rule.pattern
            BlockKind.PREFIX -> {
                val n = PhoneNumbers.stripFormatting(number)
                val pat = rule.pattern
                // Accept both national (03…) and international (+9723…) spellings of the same prefix.
                n.startsWith(pat) ||
                    (pat.startsWith("0") && n.startsWith("+972" + pat.drop(1))) ||
                    (pat.startsWith("+972") && n.startsWith("0" + pat.drop(4)))
            }
        }
    }
}
