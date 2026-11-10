# Calepinage

Appli de calepinage (doublage pierre + fibre de bois + Vario Xtra + ossature + BA13), mur par mur.

- `www/index.html` : l'appli web complète, hors ligne.
- `android/` : projet Android Capacitor (appli native qui affiche `www/`).
- GitHub Actions construit l'APK à chaque envoi sur `main` et la publie dans les Releases.

L'APK est signée avec la même clé que les versions 1.x : elle s'installe par-dessus, et les projets de l'ancienne appli sont repris au premier lancement.
