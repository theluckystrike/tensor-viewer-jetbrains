package zovo.tensor;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The IDE runs this only for files whose name gives no file type. Without it, a double-click on a .npy, .npz or
 * PyTorch .pt/.pth/.ckpt/.bin file opens the "Register New File Type Association" dialog before any editor.
 * When another plugin owns the extension (for example "NumPy Array" from the NetCDF, HDF5 &amp; NumPy Viewer),
 * the IDE never asks this detector, so there is no file type conflict.
 */
public final class TensorFileTypeDetector implements FileTypeRegistry.FileTypeDetector {
    @Override
    public @Nullable FileType detect(@NotNull VirtualFile file, @NotNull ByteSequence firstBytes, @Nullable CharSequence firstCharsIfText) {
        int n = Math.min(firstBytes.length(), 512);
        byte[] head = new byte[n];
        for (int i = 0; i < n; i++) head[i] = firstBytes.byteAt(i);
        return matches(TensorEditorProvider.ext(file.getExtension()), head, n) ? TensorFileType.DATA : null;
    }

    /** .npy: the NumPy magic. .npz: a zip. .pt/.pth/.ckpt/.bin: the same PyTorch check as the editor provider. */
    public static boolean matches(@Nullable String ext, byte[] head, int n) {
        if (ext == null) return false;
        switch (ext) {
            case "npy": return n >= 6 && (head[0] & 0xff) == 0x93 && head[1] == 'N' && head[2] == 'U' && head[3] == 'M' && head[4] == 'P' && head[5] == 'Y';
            case "npz": return n >= 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4;
            case "pt": case "pth": case "ckpt": case "bin": return TensorEditorProvider.isTorch(ext, head, n);
            default: return false;
        }
    }

    @Override public int getDesiredContentPrefixLength() { return 512; }
}
