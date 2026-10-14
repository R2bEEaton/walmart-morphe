# Walmart Morphe Patches

Route My List enhancements for the Walmart Android app.

## ❓ About

Includes an in-store route map for eligible shopping-list items, an item carousel with aisle/section details, and native Walmart map actions.

### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=R2bEEaton/walmart-morphe

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v3.0.1](https://github.com/R2bEEaton/walmart-morphe/releases/tag/v3.0.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;6 patches total
<details open>
<summary>📦 Walmart&nbsp;&nbsp;•&nbsp;&nbsp;6 patches</summary>
<br>

**🎯 Supported versions:**

| 26.38 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Default Lists Tab](#default-lists-tab) | Opens the 'Lists' tab by default when tapping 'My Items' instead of the 'Reorder' tab. |  |
| [Find Item for Linked Items](#find-item-for-linked-items) | Restores the 'Find item' button on linked shopping list items so you can link a different product. |  |
| [Flash Everywhere](#flash-everywhere) | Adds a dedicated 'Flash tag' sibling pill button next to Aisle indicators throughout the app to illuminate electronic shelf tags directly. |  |
| [Real-Time Stock & Inventory Display](#real-time-stock-inventory-display) | Displays real-time in-store stock availability badges on list and checklist items (e.g. In Stock, Low Stock / Only X left, Out of Stock). |  |
| [Remove Sponsored Ads](#remove-sponsored-ads) | Removes sponsored product carousels, banner ads, and promotional placements for a faster, cleaner app experience. |  |
| [Route My List](#route-my-list) | Adds a 'Plan my route' map icon to the Walmart 'Shop in-store' checklist screen that opens Walmart's own in-store map with every list item's aisle pinned at once. |  |

</details>

<!-- PATCHES_END -->

### 🛠️ Building locally

- Run `./gradlew buildAndroid`
- The built patches .mpp file is found in `patches/build/libs/patches-*.mpp`
- Patch the mpp file using [Morphe-Desktop](https://github.com/MorpheApp/morphe-desktop)
  like any other patch bundle.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) for more information.

## 📜 License

Walmart Morphe Patches are licensed under the [GNU General Public License v3.0](LICENSE)
