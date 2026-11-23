# IronTrack

IronTrack est une application Android native de planification et de suivi d'entraînement de musculation, entièrement en français et 100 % hors ligne.

- Programmes avec alternance **Semaine A / Semaine B**, séances par jour de la semaine, heure et durée estimée facultatives.
- Séries détaillées : répétitions (fixes ou fourchette), RIR, RPE, charge et repos, **tous facultatifs** : une valeur absente reste vide, elle n'est jamais remplacée par 0.
- Saisie par molettes (pas de clavier pour les valeurs numériques).
- Séance en cours : chronomètre, durée estimée, minuteur de repos, performances précédentes, séries ajoutées à la volée.
- Calendrier mensuel et hebdomadaire, déplacement ou annulation d'une séance.
- Historique, records personnels, graphiques de progression, comparaison estimé / réel.
- **Import intelligent** de programmes : texte collé, TXT, PDF (texte ou scanné), images JPG/PNG/WEBP/HEIC, photo. Aperçu complet et modifiable avant l'enregistrement, rien n'est inventé.
- Export / restauration JSON, rappels de séance, thème sombre (par défaut), clair ou système.
- Mises à jour intégrées depuis GitHub Releases, avec vérification de l'empreinte SHA-256 et de la signature.

Android 10 (API 29) minimum.

---

## Installer IronTrack sur un téléphone

1. Sur le téléphone, ouvrez la page [Releases](https://github.com/SageeMuffin/IronTrack/releases/latest) du dépôt.
2. Téléchargez `IronTrack-vX.Y.Z.apk`.
3. (Recommandé) Vérifiez l'empreinte : elle doit correspondre au fichier `IronTrack-vX.Y.Z.apk.sha256` publié à côté.
4. Ouvrez l'APK. Android demande d'autoriser votre navigateur (ou gestionnaire de fichiers) à **installer des applications inconnues** : activez « Autoriser cette source », revenez en arrière, puis touchez **Installer**.

Play Protect peut afficher un avertissement pour une application qui ne vient pas du Play Store : choisissez « Installer quand même » si l'APK provient bien de ce dépôt.

## Mettre à jour

Dans l'application : **Paramètres → À propos d'IronTrack → Rechercher une mise à jour**.

- IronTrack interroge GitHub Releases au plus une fois par 24 h automatiquement (désactivable), et ignore les brouillons et préversions (sauf option contraire).
- La mise à jour est téléchargée, puis vérifiée : taille, empreinte SHA-256, nom de paquet, numéro de version et certificat de signature identique à celui de l'application installée.
- L'installation passe toujours par l'installateur officiel d'Android, qui demande votre confirmation. La première fois, Android demande d'autoriser IronTrack à installer des applications : l'application explique la démarche et ouvre le bon écran de réglages.

Vos données sont conservées lors d'une mise à jour (même identifiant d'application, même signature).

### Version de développement et version de production

La version de développement (`IronTrack Debug`, identifiant `com.irontrack.app.debug`) est signée avec la clé de débogage locale. Elle s'installe **à côté** de la version de production (`com.irontrack.app`) et ne la remplace pas : les deux ont des données séparées. Android refuse de mettre à jour une application avec un APK signé par une autre clé ; IronTrack le détecte et n'essaie jamais de forcer l'installation. Pour passer d'une installation signée avec une autre clé à la version de production, exportez vos données (Paramètres → Exporter mes données), désinstallez l'ancienne version, installez la nouvelle puis restaurez la sauvegarde.

---

## Environnement de développement

- Android Studio (Ladybug ou plus récent) ou JDK 17 + Android SDK (plateforme 35).
- Kotlin 2.0, Jetpack Compose (Material 3), Room, Navigation Compose, DataStore, WorkManager, ML Kit (reconnaissance de texte, sur l'appareil), PdfBox-Android.

Structure :

| Module | Contenu |
| --- | --- |
| `core/` | Kotlin pur, testable sans Android : analyse des programmes importés, catalogue et rapprochement des exercices, planification A/B, formats français, versions. |
| `app/` | Application Android : base de données, écrans, import de documents (PDF/OCR), notifications, mises à jour. |

## Compiler

```bash
./gradlew :core:test                 # tests du moteur (sans SDK Android)
./gradlew :app:testDebugUnitTest     # tests unitaires de l'application
./gradlew :app:assembleDebug         # APK de développement : app/build/outputs/apk/debug/
```

La version vient de `version.properties` (`versionName=X.Y.Z`). Le `versionCode` est calculé automatiquement : `X × 10000 + Y × 100 + Z` (par exemple 1.2.3 → 10203), ce qui garantit un ordre numérique croissant.

Pour compiler localement une version de production signée, définissez les variables d'environnement (jamais dans un fichier du dépôt) :

```bash
export ANDROID_KEYSTORE_PATH="$HOME/irontrack-signing/irontrack-release.jks"
export ANDROID_KEYSTORE_PASSWORD="…"
export ANDROID_KEY_ALIAS="irontrack"
export ANDROID_KEY_PASSWORD="…"
./gradlew :app:assembleRelease
```

Sans ces variables, `assembleRelease` produit un APK non signé, que le workflow de publication refuse de publier.

## Publier une version

La publication est automatisée par `.github/workflows/release.yml`.

### Une seule fois : créer et sauvegarder la clé de production

1. Générez le keystore **hors du dépôt** :
   ```bash
   scripts/generer-keystore.sh            # crée ~/irontrack-signing/irontrack-release.jks
   ```
   Le script refuse d'écraser un keystore existant et de l'écrire dans le dépôt.
2. **Sauvegardez** le fichier `.jks` et ses deux mots de passe dans au moins deux endroits sûrs. Si cette clé est perdue, aucune mise à jour ne pourra plus être installée par-dessus les versions existantes : il faudrait désinstaller l'application.
3. Dans GitHub : **Settings → Secrets and variables → Actions → New repository secret**, créez :

   | Secret | Valeur |
   | --- | --- |
   | `ANDROID_KEYSTORE_BASE64` | résultat de `base64 -w0 ~/irontrack-signing/irontrack-release.jks` (macOS : `base64 -i …`) |
   | `ANDROID_KEYSTORE_PASSWORD` | mot de passe du keystore |
   | `ANDROID_KEY_ALIAS` | alias de la clé (`irontrack` par défaut) |
   | `ANDROID_KEY_PASSWORD` | mot de passe de la clé (identique au précédent avec un keystore PKCS12) |

Ne régénérez jamais cette clé : toutes les versions doivent être signées avec la même.

### À chaque version

1. Mettez à jour `version.properties` (par exemple `versionName=1.1.0`) et fusionnez sur la branche principale.
2. Créez et poussez un tag annoté (son message sert de notes de version) :
   ```bash
   git tag -a v1.1.0 -m "Nouveautés : …"
   git push origin v1.1.0
   ```
3. Le workflow vérifie le format du tag et l'absence de release existante, restaure le keystore dans un dossier temporaire, lance les tests, compile l'APK signé, vérifie la signature avec `apksigner`, calcule l'empreinte SHA-256 et crée la release avec :
   - `IronTrack-v1.1.0.apk`
   - `IronTrack-v1.1.0.apk.sha256`

Le workflow échoue avec un message explicite si un secret manque, si le tag est mal formé, si la release existe déjà ou si l'APK n'est pas signé. Un APK non signé n'est jamais publié.

## Sécurité

- Aucun identifiant de signature dans le dépôt, le code ou les journaux : uniquement des variables d'environnement et des secrets GitHub Actions. Le `.gitignore` exclut `*.jks`, `*.keystore`, `*.p12`, `*.pem`, `keystore.properties`.
- Actions GitHub épinglées par empreinte de commit, permissions minimales (`contents: write` uniquement pour la création de la release).
- L'application n'installe jamais rien silencieusement et ne contourne pas les autorisations d'Android.
