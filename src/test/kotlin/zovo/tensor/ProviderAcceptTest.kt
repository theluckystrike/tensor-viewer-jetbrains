package zovo.tensor

import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import zovo.jcefshell.ShellEditor
import java.io.File

class ProviderAcceptTest : BasePlatformTestCase() {
    private val provider = TensorEditorProvider()
    private val fixtures get() = File(System.getProperty("tensor.samples"))
    private fun vf(rel: String): VirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(fixtures, rel))!!

    fun testAcceptsModelFilesByName() {
        for (ext in listOf("gguf", "safetensors", "npy", "npz", "GGUF", "SafeTensors", "NPY")) {
            assertTrue(ext, provider.accept(project, myFixture.addFileToProject("a/x.$ext", "").virtualFile))
        }
        for (ext in listOf("txt", "json", "py", "ggml", "onnx", "h5")) {
            assertFalse(ext, provider.accept(project, myFixture.addFileToProject("c/x.$ext", "").virtualFile))
        }
        assertEquals(FileEditorPolicy.HIDE_DEFAULT_EDITOR, provider.policy)
    }

    fun testPytorchFilesOnlyByContent() {
        for (f in listOf("torch/checkpoint.pth", "torch/dtypes.pt", "torch/legacy.pth", "torch/lightning.ckpt", "torch/pytorch_model.bin", "torch/scripted.pt", "torch/state_dict.pt")) {
            assertTrue(f, provider.accept(project, vf(f)))
        }
        // A site-packages path file, a Chameleon template, an empty .pt and other .bin files keep their usual editor.
        assertFalse(provider.accept(project, myFixture.addFileToProject("site-packages/distutils-precedence.pth", "import os; var = 'SETUPTOOLS_USE_DISTUTILS'\n/opt/lib\n").virtualFile))
        assertFalse(provider.accept(project, myFixture.addFileToProject("templates/page.pt", "<html xmlns:tal=\"http://xml.zope.org/namespaces/tal\"><p tal:content=\"x\"/></html>").virtualFile))
        assertFalse(provider.accept(project, myFixture.addFileToProject("e/empty.pt", "").virtualFile))
        assertFalse(provider.accept(project, myFixture.addFileToProject("b/firmware.bin", "\u0001\u0002binary-ish data").virtualFile))
        val legacy = byteArrayOf(0x80.toByte(), 2, 0x8a.toByte(), 10, 0, 0, 0, 0, 0, 0, 0, 0)
        assertFalse(".bin pickle is not taken", TensorEditorProvider.isTorch("bin", legacy, legacy.size))
        assertTrue(TensorEditorProvider.isTorch("pth", legacy, legacy.size))
        val otherZip = File(fixtures, "bundle_stored.npz").readBytes().copyOf(512)
        assertFalse("a zip without data.pkl is not a PyTorch .bin", TensorEditorProvider.isTorch("bin", otherZip, otherZip.size))
    }

    fun testOpensSampleGgufInTheViewer() {
        val file = vf("tiny-llama-q8.gguf")
        assertTrue(provider.accept(project, file))
        // The light test editor manager opens text editors only, so ask the provider registry and create the editor directly.
        val providers = FileEditorProviderManager.getInstance().getProviderList(project, file)
        assertTrue(providers.map { it.javaClass.name }.toString(), providers.any { it is TensorEditorProvider })
        assertEquals("GGUF Model", file.fileType.name)
        assertEquals("Safetensors Model", vf("gpt2_tiny.safetensors").fileType.name)
        val editor = provider.createEditor(project, file)
        try {
            assertTrue(editor is ShellEditor)
            assertEquals(file, editor.file)
            assertFalse(editor.isModified)
            assertTrue(editor.component.isEnabled)
            // JCEF is off in unit tests, so this is the plain fallback: the GGUF header parsed in Kotlin.
            assertNull((editor as ShellEditor).token)
        } finally {
            Disposer.dispose(editor)
        }
    }

    fun testHeaderFallbackForGgufSafetensorsNpyAndTorch() {
        val g = HeaderParser.describe(File(fixtures, "tiny-llama-q8.gguf"))
        assertTrue(g, g.startsWith("GGUF version 3\ntensors: "))
        assertTrue(g, g.contains("general.architecture = llama"))
        assertTrue(g, g.contains("Q8_0"))
        assertTrue(g, g.contains("parameters: "))
        val s = HeaderParser.describe(File(fixtures, "gpt2_tiny.safetensors"))
        assertTrue(s, s.startsWith("safetensors\ntensors: "))
        assertTrue(s, Regex("dtypes: [A-Z0-9]+ x\\d+").containsMatchIn(s))
        val bf = HeaderParser.describe(File(fixtures, "bf16_f8.safetensors"))
        assertTrue(bf, bf.contains("BF16"))
        assertTrue(HeaderParser.describe(File(fixtures, "broken_header.safetensors")).startsWith("Not a"))
        assertEquals("NumPy .npy version 1\ndtype: <f4\nshape: (2, 3)\nfortran_order: False", HeaderParser.describe(File(fixtures, "arange_2x3_f4.npy")))
        assertTrue(HeaderParser.describe(File(fixtures, "torch/checkpoint.pth")).startsWith("PyTorch checkpoint (zip)"))
        assertTrue(HeaderParser.describe(File(fixtures, "torch/legacy.pth")).startsWith("PyTorch checkpoint (pickle"))
    }

    fun testHeaderFallbackOnA20GbSparseGguf() {
        // The real fixture's header, then a seek to 20 GB: a sparse file, no real disk use. Deleted at the end.
        val dir = File(System.getProperty("tensor.testTmp"), "sparse-gguf").apply { mkdirs() }
        try {
            val head = File(fixtures, "tiny-llama-q8.gguf").readBytes()
            val big = File(dir, "big.gguf")
            java.io.RandomAccessFile(big, "rw").use { it.write(head); it.setLength(20L * 1024 * 1024 * 1024) }
            val t0 = System.nanoTime()
            val text = HeaderParser.describe(big)
            val ms = (System.nanoTime() - t0) / 1_000_000
            assertTrue(text, text.contains("general.architecture = llama"))
            assertTrue(text, text.contains("of 21,474,836,480"))
            assertTrue("took $ms ms", ms < 2000)
        } finally { dir.deleteRecursively() }
    }

    fun testUnclaimedNumpyAndTorchFilesGetTensorDataTypeByContent() {
        // Without this the IDE opens "Register New File Type Association" on a double-click (seen in the real-IDE gate).
        for (f in listOf("arange_2x3_f4.npy", "bundle_stored.npz", "torch/checkpoint.pth", "torch/dtypes.pt", "torch/legacy.pth", "torch/lightning.ckpt", "torch/pytorch_model.bin")) {
            // Unknown = the IDE would show the dialog. A type another plugin gives (.pt is a TextMate type) is fine: the provider still opens it.
            assertFalse(f, vf(f).fileType is com.intellij.openapi.fileTypes.UnknownFileType)
        }
        for (f in listOf("arange_2x3_f4.npy", "bundle_stored.npz", "torch/pytorch_model.bin")) assertEquals(f, "Tensor Data", vf(f).fileType.name)
        val text = myFixture.addFileToProject("d/firmware.bin", "plain bytes, not a model").virtualFile
        assertFalse(TensorFileTypeDetector.matches("bin", "plain bytes, not a model".toByteArray(), 24))
        assertFalse("Tensor Data" == text.fileType.name)
        assertFalse(TensorFileTypeDetector.matches("npy", "PK\u0003\u0004".toByteArray(), 4))
        assertTrue(TensorFileTypeDetector.matches("npz", byteArrayOf(0x50, 0x4b, 3, 4), 4))
    }
}
