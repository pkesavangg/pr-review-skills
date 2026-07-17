package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account

/**
 * Sorts accounts: active account first, then others by lastActiveTime descending.
 *
 * Shared by [AccountService] and its collaborators (extracted under MOB-1499) so the ordering
 * logic isn't duplicated across the split. Behaviour is identical to the previous private helper.
 */
internal fun List<Account>.sortedActiveFirst(): List<Account> {
  AppLog.d("AccountService", "sortedActiveFirst() called. Sorting accounts with active account first.")
  val active = this.find { it.isActiveAccount }
  val others =
    this
      .filter { !it.isActiveAccount }
      .sortedByDescending { it.lastActiveTime?.toLongOrNull() ?: 0L }
  return listOfNotNull(active) + others
}
