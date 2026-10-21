package com.example.overdex.data

import com.example.overdex.battle.artifact.CropArtifactReference
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** One unaltered device-capture reference, outside match exports and profile coordinates. */
class BattleCalibrationReferenceStore(private val filesDir: File) {
    private val directory get() = File(filesDir, "calibration_reference")
    fun latest(): File? = File(directory, "latest_battle.png").takeIf { it.isFile && it.length() > 0 }

    fun remember(reference: CropArtifactReference): Boolean = runCatching {
        // A crop or a masked crop atlas is not a full-screen calibration reference.
        // The caller supplies preserveSourceFrame's full-frame artifact only.
        require(reference.relativePath.matches(Regex("artifacts/frames/sha256/[a-f0-9]{64}\\.png")))
        val source = File(filesDir, reference.relativePath)
        require(source.isFile && source.length() == reference.byteCount)
        check(directory.isDirectory || directory.mkdirs())
        val temporary = File.createTempFile("battle-", ".png", directory)
        try {
            Files.copy(source.toPath(), temporary.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.move(temporary.toPath(), File(directory, "latest_battle.png").toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
        true
    }.getOrDefault(false)
}
