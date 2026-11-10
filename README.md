# Calepinage Murs — appli Android

Appli de calepinage (ossature métallique, fibre de bois chevillée, membrane Vario Xtra, plaques, finitions Mildecor) avec métré, visite 3D, réseaux et mode chantier. Elle fonctionne entièrement hors ligne.

## Installer sur le téléphone

1. Ouvrir **Releases** (à droite sur la page du dépôt) puis la dernière version.
2. Télécharger `Calepinage.apk` et l'ouvrir.
3. Autoriser « Installer des applis inconnues » pour le navigateur, une seule fois.

Chaque modification envoyée sur `main` reconstruit l'APK automatiquement (onglet **Actions**).

## Contenu

- `app/src/main/assets/index.html` : l'appli complète (three.js inclus).
- `app/src/main/java/.../MainActivity.java` : la WebView plein écran, l'ouverture et l'enregistrement de fichiers, l'impression.
