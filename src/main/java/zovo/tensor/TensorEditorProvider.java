package zovo.tensor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zovo.jcefshell.ShellEditor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Opens GGUF, safetensors, PyTorch and NumPy files in the read-only tensor viewer instead of the "binary file" editor.
 * .pt, .pth, .ckpt and .bin are taken only when the first bytes say PyTorch: plain-text .pth path files from
 * site-packages, Chameleon .pt templates and other .bin files keep their usual editor.
 */
public final class TensorEditorProvider implements FileEditorProvider, DumbAware {
    /** Always ours, by extension. */
    public static final Set<String> BY_NAME = Set.of("gguf", "safetensors", "npy", "npz");
    /** Ours only when the content is a PyTorch zip (or a legacy pickle for .pt, .pth, .ckpt). */
    public static final Set<String> BY_CONTENT = Set.of("pt", "pth", "ckpt", "bin");

    static @Nullable String ext(@Nullable String extension) { return extension == null ? null : extension.toLowerCase(Locale.ROOT); }

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        if (file.isDirectory()) return false;
        String e = ext(file.getExtension());
        if (e == null) return false;
        if (BY_NAME.contains(e)) return true;
        if (!BY_CONTENT.contains(e) || file.getLength() < 4) return false;
        byte[] head = new byte[512];
        int n;
        try (InputStream in = file.getInputStream()) { n = in.readNBytes(head, 0, head.length); }
        catch (IOException ex) { return false; }
        return isTorch(e, head, n);
    }

    /**
     * .pt, .pth and .ckpt: any zip (torch.save and TorchScript archives) or a legacy torch.save() pickle (0x80, protocol 2 to 5).
     * Text files never start with these bytes, so .pth path files and .pt templates stay in their editor.
     * .bin: only a zip whose first entry is "&lt;archive&gt;/data.pkl", as torch.save writes it; other .bin files are left alone.
     */
    public static boolean isTorch(@NotNull String ext, byte[] head, int n) {
        boolean zip = n >= 30 && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4;
        if ("bin".equals(ext)) {
            if (!zip) return false;
            int nameLen = (head[26] & 0xff) | (head[27] & 0xff) << 8;
            if (nameLen <= 0 || 30 + nameLen > n) return false;
            String first = new String(head, 30, nameLen, StandardCharsets.UTF_8);
            return first.endsWith("/data.pkl") || first.equals("data.pkl");
        }
        return zip || (n >= 2 && (head[0] & 0xff) == 0x80 && head[1] >= 2 && head[1] <= 5);
    }

    @Override
    public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        return new ShellEditor(project, file, "tensor", "Tensor Viewer", Support.INSTANCE::footerHtml, RatingState.INSTANCE::report, HeaderParser.INSTANCE::describe);
    }

    @Override public @NotNull String getEditorTypeId() { return "zovo-tensor-viewer"; }
    @Override public @NotNull FileEditorPolicy getPolicy() { return FileEditorPolicy.HIDE_DEFAULT_EDITOR; }
}
