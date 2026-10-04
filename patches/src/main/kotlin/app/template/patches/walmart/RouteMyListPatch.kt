package app.template.patches.walmart

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS = "Lapp/template/extension/extension/WalmartRouteMyList;"

// Traced from a jadx decompile of Walmart Android v26.38 (versionCode 26380016).
// Obfuscated names below (Z0, kb, Md, the field "a") are specific to this exact build and will
// need re-tracing for any other Walmart version.
val walmartCompatibility = Compatibility(
    name = "Walmart",
    packageName = "com.walmart.android",
    appIconColor = 0x0071CE,
    apkFileType = ApkFileType.APKM,
    targets = listOf(
        AppTarget(version = "26.38"),
    ),
)

// ListDetailFragment's toolbar MenuProvider. kb() = onCreateMenu, Md() = onMenuItemSelected.
object ListDetailMenuProviderCreateFingerprint : Fingerprint(
    definingClass = "Lcom/walmart/glass/lists/view/lists/Z0;",
    name = "kb",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/view/Menu;", "Landroid/view/MenuInflater;"),
)

object ListDetailMenuProviderSelectedFingerprint : Fingerprint(
    definingClass = "Lcom/walmart/glass/lists/view/lists/Z0;",
    name = "Md",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Landroid/view/MenuItem;"),
)

// Ze(boolean) only calls addMenuProvider(new Z0(this), ...) when a CCM/remote-config flag
// (com.walmart.glass.lists.config.f.I()) is true. Rather than hand-write the addMenuProvider
// call in raw smali (uncertain register layout, high crash risk), force that one boolean
// call's result to always be true so the existing, real addMenuProvider call always runs.
object ListDetailFragmentZeFingerprint : Fingerprint(
    definingClass = "Lcom/walmart/glass/lists/view/lists/ListDetailFragment;",
    name = "Ze",
    returnType = "V",
    parameters = listOf("Z"),
)

// Route My List's native fragment owns both the real shelf-label capability check and its
// timer/cooldown state.  Hook it after its layout is created so the extension can ask that
// state machine to render, then present the native action as a compact affordance.
object NativeRouteMyListViewCreatedFingerprint : Fingerprint(
    definingClass = "Lcom/walmart/glass/instoremaps/view/InStoreMapsMultiItemLocatorFragment;",
    name = "onViewCreated",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
)

val routeMyListPatch = bytecodePatch(
    name = "Route My List",
    description = "Adds a 'Plan my route' button to the Walmart shopping list screen that opens " +
        "Walmart's own in-store map with every list item's aisle pinned at once.",
    default = true,
) {
    compatibleWith(walmartCompatibility)

    extendWith("extensions/extension.mpe")

    execute {
        // Append just before the final return-void, so our item survives the menu.clear() call
        // at the top of the original method instead of being wiped by it.
        val kbMethod = ListDetailMenuProviderCreateFingerprint.method
        kbMethod.addInstructions(
            kbMethod.instructions.size - 1,
            "invoke-static {p1}, $EXTENSION_CLASS->addRouteMenuItem(Landroid/view/Menu;)V",
        )

        // Original body is just `return true` unconditionally, so it's safe to fully replace it:
        // read the Z0.a field (the owning ListDetailFragment), hand it + the MenuItem to the
        // extension, and return its result directly.
        val mdMethod = ListDetailMenuProviderSelectedFingerprint.method
        mdMethod.addInstructions(
            0,
            """
                iget-object v0, p0, Lcom/walmart/glass/lists/view/lists/Z0;->a:Lcom/walmart/glass/lists/view/lists/ListDetailFragment;
                invoke-static {p1, v0}, $EXTENSION_CLASS->onMenuItemSelected(Landroid/view/MenuItem;Ljava/lang/Object;)Z
                move-result v0
                return v0
            """,
        )

        val zeMethod = ListDetailFragmentZeFingerprint.method
        val callIndex = zeMethod.instructions.indexOfFirst { insn ->
            insn is ReferenceInstruction &&
                (insn.reference as? MethodReference)?.let { ref ->
                    ref.name == "I" && ref.definingClass == "Lcom/walmart/glass/lists/config/f;"
                } == true
        }
        check(callIndex >= 0) { "Could not find config.f.I() call in ListDetailFragment.Ze" }
        val resultRegister = (zeMethod.instructions[callIndex + 1] as OneRegisterInstruction).registerA
        zeMethod.replaceInstruction(callIndex + 1, "const/4 v$resultRegister, 0x1")

        // Run after Walmart has installed the carousel and its click listener. The extension
        // schedules its UI work on the view, so this never races the superclass setup above.
        val nativeRouteMethod = NativeRouteMyListViewCreatedFingerprint.method
        nativeRouteMethod.addInstructions(
            nativeRouteMethod.instructions.size - 1,
            // This large fragment has p0 above the four-bit invoke register range, so use the
            // range form rather than relying on the patcher to allocate a temporary register.
            "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->onNativeRouteMyListViewCreated(Ljava/lang/Object;)V",
        )

    }
}
