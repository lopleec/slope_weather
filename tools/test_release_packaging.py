import importlib.util
import pathlib
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("release_packaging", pathlib.Path(__file__).with_name("prepare-release.py"))
RELEASE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RELEASE)


class ReleasePackagingTest(unittest.TestCase):
    fingerprint = "a" * 64

    def certificate(self, prefix, debug=False):
        return ("Verified using v2 scheme (APK Signature Scheme v2): true\n"
                + prefix + " certificate DN: CN=" + ("Android Debug" if debug else "Slope Weather") + "\n"
                + prefix + " certificate SHA-256 digest: " + self.fingerprint + "\n")

    def test_sdk_36_signer_output_is_accepted(self):
        self.assertEqual(self.fingerprint, RELEASE.signing_identity(self.certificate("Signer #1")))

    def test_sdk_37_signer_output_is_accepted(self):
        self.assertEqual(self.fingerprint, RELEASE.signing_identity(self.certificate("V2 Signer:")))

    def test_debug_certificates_are_rejected(self):
        with self.assertRaises(RuntimeError):
            RELEASE.signing_identity(self.certificate("Signer #1", debug=True))

    def test_unverified_schemes_are_rejected(self):
        with self.assertRaises(RuntimeError):
            RELEASE.signing_identity(self.certificate("Signer #1").replace(": true", ": false"))

    def test_private_material_inside_an_allowed_source_directory_is_excluded(self):
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder)
            (root / "app/src/main").mkdir(parents=True)
            for name in ("Main.kt", "private.p12", "private.jks", ".env", ".env.production", "signing.properties"):
                (root / "app/src/main" / name).write_text("fixture")
            with patch.object(RELEASE, "ROOT", root), patch.object(RELEASE, "SOURCE_PATHS", ["app/src"]):
                self.assertEqual([pathlib.Path("app/src/main/Main.kt")], RELEASE.source_files())


if __name__ == "__main__":
    unittest.main()
