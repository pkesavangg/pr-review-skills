package com.dmdbrands.gurus.weight.features.DeviceSetup.helper

import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager

/**
 * Auto-switches the dashboard header to the device the user just added (MOB-422).
 *
 * After a device-setup flow completes, the newly added device's product becomes the
 * active product and the single-product dashboard is surfaced (snapshot mode off), so the
 * header reflects what the user intentionally set up. A null [selection] is a no-op, for
 * flows that should leave the active product unchanged (e.g. baby-scale setup, which is not
 * bound to a specific baby profile at setup time).
 */
suspend fun IProductSelectionManager.switchActiveProductAfterSetup(selection: ProductSelection?) {
  selection ?: return
  // Add the connected product to the account's productTypes on the server (spec §2.19).
  persistProductForSetup(selection.productType)
  selectProduct(selection)
  setSnapshotMode(false)
}
