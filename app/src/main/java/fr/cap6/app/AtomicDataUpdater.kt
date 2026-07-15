package fr.cap6.app

import java.io.File
import java.security.MessageDigest

object AtomicDataUpdater {
    fun sha256(file: File): String {
        val md=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val b=ByteArray(64*1024); while(true){val n=input.read(b); if(n<0)break; md.update(b,0,n)} }
        return md.digest().joinToString(""){"%02x".format(it)}
    }
    fun replaceVerified(temp: File, active: File, expectedSha256: String) {
        require(temp.isFile && sha256(temp).equals(expectedSha256,true)) { "Checksum mismatch" }
        val backup=File(active.parentFile,active.name+".bak")
        if(backup.exists()) backup.delete()
        if(active.exists() && !active.renameTo(backup)) error("Cannot preserve active database")
        if(!temp.renameTo(active)) { if(backup.exists()) backup.renameTo(active); error("Atomic replacement failed") }
        backup.delete()
    }
}
