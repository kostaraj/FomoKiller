# 📱 FomoKiller

**FomoKiller** est une application Android open-source conçue pour vous redonner le contrôle sur vos distractions. Marre d'être interrompu par des notifications inutiles tout en ayant peur de rater un appel important ? FomoKiller est là pour ça.

---

## ✨ Caractéristiques

- **🛡️ 100% Local & Privé** : Pas de serveur, pas de collecte de données. Vos notifications restent sur votre téléphone.
- **🚀 Sans fioritures** : Une interface minimaliste pour une efficacité maximale.
- **⚙️ Trois modes de concentration** :
  - **Désactivé** : La vie normale. Toutes les notifications passent.
  - **Activé (Sélectif)** : Bloquez uniquement les coupables (réseaux sociaux, jeux, etc.). Tout le reste passe.
  - **Protégé (VIP)** : Le mode concentration ultime. Tout est bloqué, sauf vos applications VIP (famille, travail, urgences) et les appels système.

---

## 🛠️ Stack Technique

- **Langage** : Kotlin
- **Architecture** : Pattern Singleton pour l'état global (`AppState`) et Service lié.
- **UI** : Material Design 3, ViewBinding, BottomSheet pour la sélection d'apps.
- **Core** : `NotificationListenerService` pour l'interception chirurgicale des notifications.
- **Stockage** : `SharedPreferences` pour une persistance ultra-légère.

---

## 📂 Structure du Projet

```text
fomokiller/
├── app/
│   ├── src/main/
│   │   ├── java/com/fomokiller/
│   │   │   ├── MainActivity.kt           # Interface principale & logique UI
│   │   │   ├── FomoNotificationService.kt # Cœur du système (Interception)
│   │   │   ├── AppState.kt                # Gestion des modes et préférences
│   │   │   └── BootReceiver.kt            # Relance le service au démarrage
│   │   └── res/
│   │       ├── layout/                    # Layouts XML (Main, BottomSheet, Item)
│   │       └── values/                    # Themes, Colors, Strings
└── README.md
```

---

## 📥 Installation

Vous pouvez installer FomoKiller de deux manières :

### Option 1 : Téléchargement Direct (Recommandé)
1. Allez dans l'onglet [Releases](https://github.com/votre-compte/fomokiller/releases).
2. Téléchargez le dernier fichier `.apk`.
3. Installez-le sur votre smartphone (autorisez les sources inconnues si nécessaire).

### Option 2 : Compilation depuis les sources
1. Clonez ce dépôt.
2. Ouvrez le projet dans **Android Studio**.
3. Compilez et installez (`./gradlew assembleDebug`).

> **Note importante** : Pour fonctionner, l'application nécessite l'autorisation **"Accès aux notifications"**. L'application vous guidera vers les paramètres au premier lancement.

---

## 🔒 Confidentialité

La confidentialité n'est pas une option, c'est la base :
- **Zéro accès internet** : L'application n'a même pas la permission `INTERNET`.
- **Zéro Cloud** : Aucune donnée ne quitte jamais votre appareil.
- **Open Source** : Le code est transparent et auditable par tous.

---

## 🤝 Contribuer

1. Forkez le projet.
2. Créez votre branche (`git checkout -b feature/AmazingFeature`).
3. Commitez vos changements (`git commit -m 'Add some AmazingFeature'`).
4. Pushez sur la branche (`git push origin feature/AmazingFeature`).
5. Ouvrez une Pull Request.

---

## 📄 Licence

Distribué sous la licence MIT. Voir `LICENSE` pour plus d'informations.

---

*Développé avec ❤️ pour ceux qui veulent retrouver leur temps.*
