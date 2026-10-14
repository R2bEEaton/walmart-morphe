package app.template.patches.walmart

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

private const val EXTENSION_CLASS = "Lapp/template/extension/extension/WalmartRemoveShop;"

object BottomNavMapFingerprint : Fingerprint(
    definingClass = "Lcom/walmart/glass/integration/navigation/b;",
    name = "i",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/util/Map;",
    parameters = emptyList(),
)

object RootActivityDefaultTabFingerprint : Fingerprint(
    definingClass = "Lcom/walmart/glass/integration/root/RootActivity;",
    name = "G",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Lglass/platform/navigation/api/i;",
    parameters = listOf("Landroid/os/Bundle;", "Ljava/lang/String;"),
)

object RootActivityOnStartFingerprint : Fingerprint(
    definingClass = "Lcom/walmart/glass/integration/root/RootActivity;",
    name = "onStart",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = emptyList(),
)

val removeShopPatch = bytecodePatch(
    name = "Remove Shop",
    description = "Removes the Shop tab entirely from the bottom navigation bar and opens to My Items by default.",
    default = true,
) {
    compatibleWith(walmartCompatibility)

    extendWith("extensions/extension.mpe")

    execute {
        // 1. Remove Shop from the bottom navigation map
        val navMapMethod = BottomNavMapFingerprint.method
        navMapMethod.addInstructions(
            navMapMethod.instructions.size - 1,
            """
                invoke-static {v0}, $EXTENSION_CLASS->filterNavMap(Ljava/util/Map;)Ljava/util/Map;
                move-result-object v0
            """.trimIndent()
        )

        // 2. Default startup tab to My Items (TAB_2)
        val defaultTabMethod = RootActivityDefaultTabFingerprint.method
        defaultTabMethod.addInstructions(
            0,
            """
                invoke-static {}, $EXTENSION_CLASS->getTab2()Ljava/lang/Object;
                move-result-object v0
                check-cast v0, Lglass/platform/navigation/api/i;
                return-object v0
            """.trimIndent()
        )

        // 3. Ensure the Shop tab view is hidden if rendered
        val onStartMethod = RootActivityOnStartFingerprint.method
        onStartMethod.addInstructions(
            1,
            "invoke-static {p0}, $EXTENSION_CLASS->onActivityStart(Landroid/app/Activity;)V"
        )
    }
}
