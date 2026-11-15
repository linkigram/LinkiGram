# Инвентарь прямых подключений настроек LinkiGram

Сгенерировано `Tools/audit_feature_hooks.py`. Проверяются Java/Kotlin в `src/main/java`.

**Это поиск ссылок, не функциональный тест.** Ссылки могут быть записью, а не чтением;
функции могут использовать accessor, static import или рефлексию. Отсутствие прямой ссылки
означает кандидата для ручной проверки, не доказанный дефект. Константы и настройки
других конфигов не включены в таблицу NimarkoConfig.

- Исходников просмотрено автоматически: **2840**.
- Публичных изменяемых скалярных полей NimarkoConfig: **302**.
- Без прямых ссылок вне preferences: **144**.

## Модули LinkiGram

| Модуль | Java/Kotlin файлов |
| --- | ---: |
| `(root)` | 9 |
| `ai` | 3 |
| `api` | 4 |
| `appearance` | 8 |
| `automation` | 5 |
| `badges` | 4 |
| `banners` | 5 |
| `camera` | 15 |
| `chats` | 2 |
| `components` | 8 |
| `drawer` | 11 |
| `features` | 3 |
| `gifts` | 1 |
| `icons` | 9 |
| `infocards` | 12 |
| `local` | 3 |
| `media` | 3 |
| `mediaglow` | 1 |
| `nfc` | 5 |
| `plugins` | 76 |
| `preferences` | 58 |
| `search` | 4 |
| `security` | 1 |
| `speech` | 1 |
| `textanim` | 1 |
| `ui` | 8 |
| `updater` | 4 |
| `utils` | 74 |
| `wsbypass` | 20 |
| `xcfg` | 6 |

## Кандидаты без прямого runtime-хука

`actionbarCrossfade`, `allowSafeStars`, `archiveStoriesFromChannels`, `archiveStoriesFromUsers`, `audioSource`, `autoPauseVideo`, `autoQuoteReplies`, `botsDrawShareButton`, `bottomNavigationFloating`, `bubbleShapeGeneration`, `cameraSlowMo`, `cameraXUseHighRange`, `centerChatTitle`, `centerTitle`, `channelsDrawShareButton`, `chatGlassLevel`, `chatShortcutJumpToBegin`, `chatShortcutSavedMessages`, `classicUi`, `customBgCardAlpha`, `customChatForSavedMessages`, `customFontPath`, `customSavedMessagesDialogId`, `customTitleEnabled`, `customTitleText`, `customWallpapers`, `deleteForAll`, `disableAttachCamera`, `disablePremStickAnim`, `disablePremStickAutoPlay`, `disablePremiumStatuses`, `disableReactionAnim`, `disableReactionsOverlay`, `disableSendHints`, `disableSwipeToNext`, `discussInsteadOfMute`, `doubleTapOutgoingAction`, `doubletapaction`, `drawSnowInActionBar`, `drawSnowInChat`, `edgeToEdgeMode`, `editTextSuggestionsFix`, `enableMsgFilters`, `folderNameInHeader`, `foldersAtBottom`, `forceBlur`, `forumAvatarsLikeChats`, `forwardAuthorship`, `forwardCaptions`, `forwardNotify`, `forwardWithoutAuthor`, `gifSpoilers`, `glareOnElements`, `hideActionBarStatus`, `hideArchivedStories`, `hideBubbleTail`, `hideMuteUnmuteButton`, `hideProxySponsor`, `hideSearchBar`, `hideSendAsChannel`, `hideStickerTime`, `hideVideoTimestamp`, `ignoreNoForwards`, `inBubbleGradient1`, `inBubbleGradient2`, `inBubbleGradient3`, `inBubbleGradients`, `inappBrowser`, `iosStyleComposer`, `largePhotos`, `largerVoiceMessagesLayout`, `linkiAss`, `mainTabsCustomTitles`, `maxAlbumSize`, `messageslideaction`, `msgFilterTransparentMsg`, `msgFiltersChatBlacklist`, `msgFiltersChatWhitelist`, `msgFiltersCollapseAutomatically`, `msgFiltersDetectEntities`, `msgFiltersDetectTranslit`, `msgFiltersElements`, `msgFiltersExcludedChats`, `msgFiltersHideAll`, `msgFiltersHideFromBlocked`, `msgFiltersLogic`, `msgFiltersMatchExactWord`, `msgFiltersRegexPatterns`, `msgFiltersUseRegex`, `msgForwardDate`, `noAuthorship`, `noCaptions`, `notificationReactions`, `notificationSound`, `oldTimeStyle`, `onlineIndicatorInGroups`, `playGIFsAsVideos`, `playVideoOnVolume`, `preReformRussian`, `predictiveBack`, `privacyPreset`, `profileBackgroundColor`, `profileBirthDatePreview`, `profileBusinessPreview`, `profileChannelPreview`, `rearCam`, `recentEmojisAmplifier`, `recentStickersAmplifier`, `replacePunctuationMarks`, `replyBackground`, `replyBackgroundEmoji`, `replyCustomColors`, `residentNotification`, `sendOriginalPhoto`, `sendVideosAtMaxQuality`, `shareDrawStoryButton`, `shortcutBrowser`, `shortcutDeleteAll`, `showAccounts`, `showIDDC`, `showMainTabs`, `silenceNonContacts`, `sleepTimer`, `slider_gifsAmplifier`, `slider_mediaAmplifier`, `slider_stickerAmplifier`, `sortByUnread`, `stickersDrawShareButton`, `supergroupsDrawShareButton`, `systemEmoji`, `tabMode`, `tabStyleStroke`, `tabletMode`, `tabsHideAllChats`, `unarchiveOnSwipe`, `unreadBadgeOnBackButton`, `usersDrawShareButton`, `vibrateInChats`, `videoMessagesHintCount`, `videoMessagesResolution`, `voiceTranscriptionProvider`, `weekdayNearDate`, `wsAutoSubscribed`, `wsInstallId`

## Все скалярные настройки NimarkoConfig

| Поле | Ссылок вне preferences | Примеры |
| --- | ---: | --- |
| `actionbarCrossfade` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `actionsBarLeftButton` | 12 | `app/nimarkogram/messenger/NimarkoResourcesHelper.java:14`<br>`app/nimarkogram/messenger/NimarkoResourcesHelper.java:30`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatHelper2.java:149` |
| `adminsAdministrators` | 2 | `app/nimarkogram/messenger/utils/chats/NimarkoChatMenuInjector.java:142`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatMenuInjector.java:156` |
| `adminsMembers` | 2 | `app/nimarkogram/messenger/utils/chats/NimarkoChatMenuInjector.java:142`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatMenuInjector.java:159` |
| `adminsPermissions` | 3 | `app/nimarkogram/messenger/utils/chats/NimarkoChatMenuInjector.java:141`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatMenuInjector.java:151`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatMenuInjector.java:162` |
| `adminsReactions` | 2 | `app/nimarkogram/messenger/utils/chats/NimarkoChatMenuInjector.java:141`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatMenuInjector.java:147` |
| `adminsRecentActions` | 2 | `app/nimarkogram/messenger/utils/chats/NimarkoChatMenuInjector.java:143`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatMenuInjector.java:169` |
| `adminsStatistics` | 2 | `app/nimarkogram/messenger/utils/chats/NimarkoChatMenuInjector.java:143`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatMenuInjector.java:166` |
| `allowProtectedScreenshots` | 6 | `org/telegram/ui/Cells/ChatMessageCell.java:10552`<br>`org/telegram/ui/ChatActivity.java:8928`<br>`org/telegram/ui/PhotoViewer.java:17452` |
| `allowSafeStars` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `allowSystemPasscode` | 4 | `app/nimarkogram/messenger/security/NimarkoBiometricPrompt.java:140`<br>`app/nimarkogram/messenger/security/NimarkoBiometricPrompt.java:37`<br>`app/nimarkogram/messenger/utils/BiometricPrompt.java:67` |
| `archiveStoriesFromChannels` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `archiveStoriesFromUsers` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `askBiometricsBeforeDelete` | 2 | `app/nimarkogram/messenger/utils/CGCompat.java:19`<br>`app/nimarkogram/messenger/utils/CGCompat.java:23` |
| `askBiometricsToOpenArchive` | 4 | `app/nimarkogram/messenger/utils/CGCompat.java:61`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatsPasscodeHelper.java:176`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatsPasswordHelper.java:121` |
| `askBiometricsToOpenChat` | 10 | `app/nimarkogram/messenger/utils/CGCompat.java:47`<br>`app/nimarkogram/messenger/utils/CGCompat.java:53`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatHelper2.java:64` |
| `askBiometricsToOpenEncrypted` | 9 | `app/nimarkogram/messenger/utils/CGCompat.java:57`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatHelper2.java:65`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatHelper2.java:72` |
| `askPasscodeBeforeDelete` | 3 | `app/nimarkogram/messenger/utils/CGCompat.java:23`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatsPasscodeHelper.java:212`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatsPasswordHelper.java:166` |
| `audioSource` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `autoPauseVideo` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `autoQuoteReplies` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `avatarCorners` | 3 | `com/exteragram/messenger/ExteraConfig.java:101`<br>`com/exteragram/messenger/ExteraConfig.java:62`<br>`com/exteragram/messenger/ExteraConfig.java:79` |
| `blockSponsoredAds` | 1 | `org/telegram/messenger/MessagesController.java:21941` |
| `botsDrawShareButton` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `bottomNavigationFloating` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `bubbleShapeGeneration` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `cameraContinuousFocus` | 2 | `app/nimarkogram/messenger/camera/CameraXUtils.java:1105`<br>`org/telegram/messenger/camera/Camera2Session.java:932` |
| `cameraExposureIndex` | 2 | `app/nimarkogram/messenger/camera/NimarkoCameraXController.java:1631`<br>`app/nimarkogram/messenger/camera/NimarkoCameraXView.java:613` |
| `cameraFaceDetection` | 2 | `app/nimarkogram/messenger/camera/CameraXUtils.java:1129`<br>`org/telegram/messenger/camera/Camera2Session.java:958` |
| `cameraNoiseReduction` | 2 | `app/nimarkogram/messenger/camera/CameraXUtils.java:1115`<br>`org/telegram/messenger/camera/Camera2Session.java:940` |
| `cameraOpticalStabilization` | 2 | `app/nimarkogram/messenger/camera/CameraXUtils.java:1039`<br>`org/telegram/messenger/camera/Camera2Session.java:920` |
| `cameraResolution` | 4 | `app/nimarkogram/messenger/camera/CameraXUtils.java:200`<br>`app/nimarkogram/messenger/camera/CameraXUtils.java:201`<br>`app/nimarkogram/messenger/camera/CameraXUtils.java:830` |
| `cameraSlowMo` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `cameraStabilisation` | 4 | `app/nimarkogram/messenger/camera/CameraXUtils.java:1048`<br>`app/nimarkogram/messenger/camera/CameraXUtils.java:1061`<br>`app/nimarkogram/messenger/camera/CameraXUtils.java:1093` |
| `cameraType` | 5 | `app/nimarkogram/messenger/camera/CameraTypeSelector.java:194`<br>`app/nimarkogram/messenger/camera/CameraTypeSelector.java:292`<br>`app/nimarkogram/messenger/camera/CameraTypeSelector.java:68` |
| `cameraXFpsRange` | 3 | `app/nimarkogram/messenger/camera/CameraXUtils.java:1158`<br>`org/telegram/messenger/camera/Camera2Session.java:895`<br>`org/telegram/ui/Components/InstantCameraView.java:4312` |
| `cameraXUseHighRange` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `centerCameraControlButtons` | 1 | `org/telegram/ui/Components/InstantCameraView.java:594` |
| `centerChatTitle` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `centerTitle` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `channelsDrawShareButton` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `chatGlassLevel` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `chatShortcutJumpToBegin` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `chatShortcutSavedMessages` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `classicUi` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `customBgCardAlpha` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `customBgDimPercent` | 1 | `app/nimarkogram/messenger/NimarkoWallpaper.java:156` |
| `customBgEnabled` | 2 | `app/nimarkogram/messenger/NimarkoWallpaper.java:36`<br>`app/nimarkogram/messenger/NimarkoWallpaper.java:40` |
| `customBgPath` | 1 | `app/nimarkogram/messenger/NimarkoWallpaper.java:41` |
| `customChatForSavedMessages` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `customFontPath` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `customSavedMessagesDialogId` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `customTitleEnabled` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `customTitleText` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `customWallpapers` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `deleteForAll` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `deletedGiftsInject` | 2 | `app/nimarkogram/messenger/gifts/NimarkoDeletedGiftsManager.java:198`<br>`app/nimarkogram/messenger/gifts/NimarkoDeletedGiftsManager.java:79` |
| `dialogsMenuShowNewGroup` | 1 | `org/telegram/ui/DialogsActivity.java:13629` |
| `dialogsMenuShowReadAll` | 1 | `org/telegram/ui/DialogsActivity.java:13622` |
| `dialogsMenuShowSaved` | 1 | `org/telegram/ui/DialogsActivity.java:13636` |
| `dialogsMenuShowSettings` | 1 | `org/telegram/ui/DialogsActivity.java:13677` |
| `dialogsMenuShowSmartCenter` | 1 | `org/telegram/ui/DialogsActivity.java:13521` |
| `dialogsMenuShowTheme` | 1 | `org/telegram/ui/DialogsActivity.java:13585` |
| `disableAttachCamera` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `disableDividers` | 1 | `app/nimarkogram/messenger/appearance/MaterialStyle.java:15` |
| `disablePremStickAnim` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `disablePremStickAutoPlay` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `disablePremiumStatuses` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `disableReactionAnim` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `disableReactionsOverlay` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `disableSendHints` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `disableSwipeToNext` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `disableVibration` | 7 | `app/nimarkogram/messenger/utils/VibrateUtil.kt:36`<br>`app/nimarkogram/messenger/utils/VibrateUtils.java:114`<br>`app/nimarkogram/messenger/utils/VibrateUtils.java:55` |
| `discussInsteadOfMute` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `doubleTapAction` | 3 | `app/nimarkogram/messenger/utils/chats/DoubleTapUtils.java:141`<br>`app/nimarkogram/messenger/utils/chats/DoubleTapUtils.java:146`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatActivityHelper.java:500` |
| `doubleTapOutgoingAction` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `doubletapaction` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `downloadSpeedBoost` | 3 | `org/telegram/messenger/FileLoadOperation.java:305`<br>`org/telegram/messenger/FileLoadOperation.java:863`<br>`org/telegram/messenger/FileLoadOperation.java:904` |
| `drawSnowInActionBar` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `drawSnowInChat` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `edgeToEdgeMode` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `editTextSuggestionsFix` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `enableMsgFilters` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `enableSaveEditsHistory` | 2 | `org/telegram/messenger/MessagesStorage.java:16625`<br>`org/telegram/ui/ChatActivity.java:47460` |
| `folderGroupsJson` | 1 | `app/nimarkogram/messenger/utils/folders/NimarkoFoldersHelper.java:163` |
| `folderNameInHeader` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `folderSwipeEnabled` | 1 | `app/nimarkogram/messenger/utils/folders/NimarkoFoldersHelper.java:143` |
| `foldersAtBottom` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `forceBlur` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `forumAvatarsLikeChats` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `forwardAuthorship` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `forwardCaptions` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `forwardNotify` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `forwardWithoutAuthor` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `ghostOfflineAfterSend` | 4 | `com/radolyn/ayugram/utils/AyuGhostUtils.java:105`<br>`com/radolyn/ayugram/utils/AyuGhostUtils.java:157`<br>`com/radolyn/ayugram/utils/AyuGhostUtils.java:269` |
| `ghostOnline` | 5 | `com/radolyn/ayugram/utils/AyuGhostUtils.java:236`<br>`com/radolyn/ayugram/utils/AyuGhostUtils.java:269`<br>`com/radolyn/ayugram/utils/AyuGhostUtils.java:271` |
| `ghostReadAfterSend` | 1 | `com/radolyn/ayugram/utils/AyuGhostUtils.java:251` |
| `ghostReadReceipts` | 5 | `com/radolyn/ayugram/utils/AyuGhostUtils.java:217`<br>`com/radolyn/ayugram/utils/AyuGhostUtils.java:221`<br>`com/radolyn/ayugram/utils/AyuGhostUtils.java:251` |
| `ghostRecording` | 2 | `com/radolyn/ayugram/utils/AyuGhostUtils.java:203`<br>`org/telegram/ui/ChatActivity.java:1437` |
| `ghostStories` | 2 | `com/radolyn/ayugram/utils/AyuGhostUtils.java:228`<br>`org/telegram/ui/ChatActivity.java:1439` |
| `ghostTyping` | 3 | `com/radolyn/ayugram/utils/AyuGhostUtils.java:198`<br>`com/radolyn/ayugram/utils/AyuGhostUtils.java:203`<br>`org/telegram/ui/ChatActivity.java:1436` |
| `gifSpoilers` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `glareOnElements` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `hideActionBarStatus` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `hideArchiveFromChatsList` | 1 | `app/nimarkogram/messenger/utils/chats/NimarkoChatMenuInjector.java:191` |
| `hideArchivedStories` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `hideBubbleTail` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `hideIncomingTyping` | 1 | `org/telegram/messenger/MessagesController.java:19086` |
| `hideKeyboardOnScrollIntensity` | 4 | `app/nimarkogram/messenger/utils/chats/NimarkoChatActivityHelper.java:580`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatActivityHelper.java:610`<br>`app/nimarkogram/messenger/utils/chats/NimarkoKeyboardHider.java:28` |
| `hideMuteUnmuteButton` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `hideProxySponsor` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `hideSearchBar` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `hideSendAsChannel` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `hideStickerTime` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `hideStories` | 3 | `com/exteragram/messenger/ExteraConfig.java:139`<br>`org/telegram/ui/DialogsActivity.java:12644`<br>`org/telegram/ui/DialogsActivity.java:12647` |
| `hideStoryViews` | 2 | `com/radolyn/ayugram/utils/AyuGhostUtils.java:228`<br>`org/telegram/ui/ChatActivity.java:1439` |
| `hideVideoTimestamp` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `iconReplacement` | 6 | `app/nimarkogram/messenger/icons/NimarkoIconResources.java:206`<br>`app/nimarkogram/messenger/icons/NimarkoIconResources.java:236`<br>`app/nimarkogram/messenger/icons/NimarkoIconResources.java:99` |
| `ignoreNoForwards` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `immersiveDrawerAnimation` | 1 | `app/nimarkogram/messenger/appearance/AppearanceConfig.java:82` |
| `inBubbleGradient1` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `inBubbleGradient2` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `inBubbleGradient3` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `inBubbleGradients` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `inappBrowser` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `iosStyleComposer` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `jacksonJSON_Provider` | 2 | `app/nimarkogram/messenger/ui/NimarkoJsonBottomSheet.java:666`<br>`app/nimarkogram/messenger/ui/NimarkoJsonBottomSheet.java:669` |
| `keepOpenedOnceMedia` | 3 | `app/nimarkogram/messenger/media/OnceMediaArchive.java:208`<br>`app/nimarkogram/messenger/media/OnceMediaArchive.java:76`<br>`app/nimarkogram/messenger/media/OnceMediaArchive.java:83` |
| `largePhotos` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `largerVoiceMessagesLayout` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `lastSeenCacheEnabled` | 2 | `com/radolyn/ayugram/utils/LastSeenHelper.java:153`<br>`com/radolyn/ayugram/utils/LastSeenHelper.java:28` |
| `latexRenderingEnabled` | 2 | `app/nimarkogram/messenger/utils/NimarkoLatexHelper.java:116`<br>`app/nimarkogram/messenger/utils/NimarkoLatexHelper.java:55` |
| `linkiAss` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `linkiShareEverywhere` | 5 | `app/nimarkogram/messenger/nfc/LinkiShareActivity.java:135`<br>`app/nimarkogram/messenger/nfc/LinkiShareActivity.java:138`<br>`app/nimarkogram/messenger/nfc/LinkiShareController.java:133` |
| `localPremiumEmojis` | 1 | `app/nimarkogram/messenger/utils/NimarkoLocalEmoji.java:23` |
| `lockedChatsBiometricTtlSec` | 2 | `app/nimarkogram/messenger/security/NimarkoBiometricPrompt.java:362`<br>`app/nimarkogram/messenger/security/NimarkoBiometricPrompt.java:427` |
| `mainMenuLayout` | 1 | `app/nimarkogram/messenger/appearance/AppearanceConfig.java:98` |
| `mainTabsCustomTitles` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `mainTabsForceOpenChats` | 1 | `org/telegram/ui/MainTabsActivity.java:1116` |
| `mainTabsOrder` | 1 | `app/nimarkogram/messenger/utils/ui/MainTabsManager.kt:59` |
| `mainTabsSemiTransparent` | 1 | `org/telegram/ui/MainTabsActivity.java:297` |
| `mainTabsTintColor` | 2 | `org/telegram/ui/MainTabsActivity.java:294`<br>`org/telegram/ui/MainTabsActivity.java:295` |
| `materialCards` | 3 | `org/telegram/ui/Components/UniversalFragment.java:91`<br>`org/telegram/ui/Components/UniversalRecyclerView.java:442`<br>`org/telegram/ui/Components/UniversalRecyclerView.java:481` |
| `maxAlbumSize` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `mediaGlow` | 10 | `app/nimarkogram/messenger/mediaglow/MediaGlowController.java:132`<br>`app/nimarkogram/messenger/mediaglow/MediaGlowController.java:178`<br>`app/nimarkogram/messenger/mediaglow/MediaGlowController.java:186` |
| `messageMenuHaptic` | 1 | `app/nimarkogram/messenger/ui/MessageMenuTweaks.java:109` |
| `messageSlideAction` | 3 | `app/nimarkogram/messenger/NimarkoResourcesHelper.java:45`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatHelper2.java:450`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatsHelper2.java:396` |
| `messagesSearchFilter` | 4 | `app/nimarkogram/messenger/utils/chats/NimarkoChatHelper2.java:524`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatHelper2.java:614`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatsHelper.java:700` |
| `messageslideaction` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `msgFilterTransparentMsg` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `msgFiltersChatBlacklist` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `msgFiltersChatWhitelist` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `msgFiltersCollapseAutomatically` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `msgFiltersDetectEntities` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `msgFiltersDetectTranslit` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `msgFiltersElements` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `msgFiltersExcludedChats` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `msgFiltersHideAll` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `msgFiltersHideFromBlocked` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `msgFiltersLogic` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `msgFiltersMatchExactWord` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `msgFiltersRegexPatterns` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `msgFiltersUseRegex` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `msgForwardDate` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `msgMenuItemsCompactView` | 1 | `app/nimarkogram/messenger/ui/MessageMenuCompactView.java:96` |
| `navigationDrawer` | 7 | `app/nimarkogram/messenger/appearance/AppearanceConfig.java:74`<br>`org/telegram/ui/DialogsActivity.java:14081`<br>`org/telegram/ui/DialogsActivity.java:14094` |
| `nimarkoMediaAuto` | 2 | `app/nimarkogram/messenger/media/NimarkoMediaController.java:180`<br>`app/nimarkogram/messenger/media/NimarkoMediaController.java:208` |
| `nimarkoMediaYtAsk` | 1 | `app/nimarkogram/messenger/media/NimarkoMediaController.java:253` |
| `nimarkoMediaYtFmt` | 1 | `app/nimarkogram/messenger/media/NimarkoMediaController.java:288` |
| `nimarkoTextAnim` | 2 | `app/nimarkogram/messenger/textanim/NimarkoTextAnim.java:113`<br>`app/nimarkogram/messenger/textanim/NimarkoTextAnim.java:192` |
| `nimarkoTextAnimAppear` | 1 | `app/nimarkogram/messenger/textanim/NimarkoTextAnim.java:114` |
| `nimarkoTextAnimCursor` | 1 | `app/nimarkogram/messenger/textanim/NimarkoTextAnim.java:115` |
| `nimarkoTextAnimDelete` | 1 | `app/nimarkogram/messenger/textanim/NimarkoTextAnim.java:116` |
| `nimarkoTextAnimSpoiler` | 1 | `app/nimarkogram/messenger/textanim/NimarkoTextAnim.java:117` |
| `noAuthorship` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `noCaptions` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `noRounding` | 1 | `com/exteragram/messenger/ExteraConfig.java:119` |
| `notificationReactions` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `notificationSound` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `oldTimeStyle` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `onlineIndicatorInGroups` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `openSettingsBySwipe` | 1 | `org/telegram/ui/DialogsActivity.java:2234` |
| `pendingRoundFront` | 4 | `org/telegram/ui/Components/ChatActivityEnterView.java:925`<br>`org/telegram/ui/Components/ChatActivityEnterView.java:928`<br>`org/telegram/ui/Components/ChatActivityEnterView.java:934` |
| `playGIFsAsVideos` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `playVideoOnVolume` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `pluginsCompactView` | 5 | `app/nimarkogram/messenger/plugins/ui/PluginsInfoActivity.java:48`<br>`app/nimarkogram/messenger/plugins/ui/PluginsInfoActivity.java:94`<br>`app/nimarkogram/messenger/plugins/ui/PluginsInfoActivity.java:95` |
| `pluginsDevMode` | 3 | `app/nimarkogram/messenger/plugins/PythonPluginsEngine.java:2108`<br>`app/nimarkogram/messenger/plugins/PythonPluginsEngine.java:4446`<br>`com/exteragram/messenger/ExteraConfig.java:38` |
| `pluginsDisableArtOpts` | 5 | `app/nimarkogram/messenger/plugins/ui/PluginsInfoActivity.java:100`<br>`app/nimarkogram/messenger/plugins/ui/PluginsInfoActivity.java:101`<br>`app/nimarkogram/messenger/plugins/ui/PluginsInfoActivity.java:102` |
| `pluginsEngine` | 20 | `app/nimarkogram/messenger/plugins/PluginsController.java:1130`<br>`app/nimarkogram/messenger/plugins/PluginsController.java:1185`<br>`app/nimarkogram/messenger/plugins/PluginsController.java:1276` |
| `pluginsPySdkAutoUpdate` | 1 | `com/exteragram/messenger/ExteraConfig.java:50` |
| `pluginsPySdkBetaVersions` | 1 | `com/exteragram/messenger/ExteraConfig.java:54` |
| `pluginsSafeMode` | 29 | `app/nimarkogram/messenger/plugins/PluginsController.java:1130`<br>`app/nimarkogram/messenger/plugins/PluginsController.java:1458`<br>`app/nimarkogram/messenger/plugins/PluginsController.java:1462` |
| `preReformRussian` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `predictiveBack` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `privacyPreset` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `profileBackgroundColor` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `profileBackgroundEmoji` | 2 | `app/nimarkogram/messenger/utils/NimarkoProfileActivityHelper.java:106`<br>`app/nimarkogram/messenger/utils/NimarkoProfileActivityHelper.java:116` |
| `profileBirthDatePreview` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `profileBusinessPreview` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `profileChannelPreview` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `readAllInMenu` | 2 | `org/telegram/ui/DialogsActivity.java:13622`<br>`org/telegram/ui/DialogsActivity.java:3443` |
| `rearCam` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `recentEmojisAmplifier` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `recentStickersAmplifier` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `replacePunctuationMarks` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `replyBackground` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `replyBackgroundEmoji` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `replyCustomColors` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `residentNotification` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `roundCamLogicalDisabled` | 3 | `org/telegram/ui/Components/InstantCameraView.java:1619`<br>`org/telegram/ui/Components/InstantCameraView.java:1640`<br>`org/telegram/ui/Components/InstantCameraView.java:1686` |
| `saveDeletedInChannels` | 1 | `com/radolyn/ayugram/messages/AyuSavePreferences.java:136` |
| `saveDeletedInGroups` | 1 | `com/radolyn/ayugram/messages/AyuSavePreferences.java:137` |
| `saveDeletedInPrivateChats` | 4 | `com/radolyn/ayugram/messages/AyuDeletedDialogs.java:126`<br>`com/radolyn/ayugram/messages/AyuDeletedDialogs.java:32`<br>`com/radolyn/ayugram/messages/AyuSavePreferences.java:129` |
| `saveDeletedMessageForBot` | 1 | `com/radolyn/ayugram/messages/AyuSavePreferences.java:124` |
| `saveDeletedMessageForBotUser` | 4 | `com/radolyn/ayugram/messages/AyuDeletedDialogs.java:43`<br>`com/radolyn/ayugram/messages/AyuSavePreferences.java:114`<br>`com/radolyn/ayugram/messages/AyuSavePreferences.java:96` |
| `saveDeletedMessages` | 13 | `com/radolyn/ayugram/messages/AyuDeletedDialogs.java:126`<br>`com/radolyn/ayugram/messages/AyuDeletedDialogs.java:32`<br>`com/radolyn/ayugram/messages/AyuSavePreferences.java:78` |
| `saveInArchivedChats` | 1 | `com/radolyn/ayugram/messages/AyuSavePreferences.java:141` |
| `saveLocalLastSeen` | 6 | `com/radolyn/ayugram/utils/LastSeenHelper.java:225`<br>`com/radolyn/ayugram/utils/LastSeenHelper.java:253`<br>`com/radolyn/ayugram/utils/LastSeenHelper.java:27` |
| `saveMediaFiles` | 2 | `com/radolyn/ayugram/proprietary/AyuMessageUtils.java:665`<br>`com/radolyn/ayugram/proprietary/AyuMessageUtils.java:690` |
| `saveMediaInPrivateChannels` | 2 | `com/radolyn/ayugram/proprietary/AyuMessageUtils.java:679`<br>`com/radolyn/ayugram/proprietary/AyuMessageUtils.java:703` |
| `saveMediaInPrivateChats` | 2 | `com/radolyn/ayugram/proprietary/AyuMessageUtils.java:667`<br>`com/radolyn/ayugram/proprietary/AyuMessageUtils.java:692` |
| `saveMediaInPrivateGroups` | 2 | `com/radolyn/ayugram/proprietary/AyuMessageUtils.java:683`<br>`com/radolyn/ayugram/proprietary/AyuMessageUtils.java:707` |
| `saveMediaInPublicChannels` | 2 | `com/radolyn/ayugram/proprietary/AyuMessageUtils.java:676`<br>`com/radolyn/ayugram/proprietary/AyuMessageUtils.java:700` |
| `saveMediaInPublicGroups` | 2 | `com/radolyn/ayugram/proprietary/AyuMessageUtils.java:680`<br>`com/radolyn/ayugram/proprietary/AyuMessageUtils.java:704` |
| `saveReadDate` | 2 | `com/radolyn/ayugram/messages/AyuSpyController.java:37`<br>`org/telegram/ui/ChatActivity.java:47457` |
| `sectionRadius` | 5 | `org/telegram/ui/Components/RecyclerListView.java:3484`<br>`org/telegram/ui/Components/RecyclerListView.java:3487`<br>`org/telegram/ui/Components/UniversalRecyclerView.java:442` |
| `sendOriginalPhoto` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `sendVideosAtMaxQuality` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `shareDrawStoryButton` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `shortcutBrowser` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `shortcutDeleteAll` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `showAccounts` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `showClearFromCache` | 2 | `app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:429`<br>`app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:56` |
| `showCopyMessage` | 1 | `app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:351` |
| `showCopyPhoto` | 3 | `app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:353`<br>`app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:37`<br>`app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:401` |
| `showCopyPhotoAsSticker` | 3 | `app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:355`<br>`app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:408`<br>`app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:42` |
| `showDetails` | 2 | `app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:196`<br>`app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:208` |
| `showDownloadSticker` | 1 | `app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:275` |
| `showForward` | 3 | `app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:357`<br>`app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:436`<br>`app/nimarkogram/messenger/ui/MessageMenuTweaks.java:46` |
| `showForwardWoAuthorship` | 3 | `app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:104`<br>`app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:359`<br>`app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:443` |
| `showForwardWoCaption` | 1 | `app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:236` |
| `showGetCustomReactions` | 1 | `app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:290` |
| `showIDDC` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `showJSON` | 2 | `app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:196`<br>`app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:471` |
| `showMainTabs` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `showMainTabsTitle` | 3 | `org/telegram/ui/MainTabsActivity.java:1101`<br>`org/telegram/ui/MainTabsLayout.java:153`<br>`org/telegram/ui/MainTabsLayout.java:169` |
| `showPencilIcon` | 2 | `app/nimarkogram/messenger/utils/chats/NimarkoChatHelper.java:180`<br>`app/nimarkogram/messenger/utils/chats/NimarkoChatsHelper.java:166` |
| `showRPCErrors` | 3 | `app/nimarkogram/messenger/utils/BiometricPrompt.java:217`<br>`app/nimarkogram/messenger/utils/BiometricPrompt.java:224`<br>`app/nimarkogram/messenger/utils/BiometricPrompt.java:231` |
| `showRepeatMessage` | 1 | `org/telegram/ui/ChatActivity.java:32747` |
| `showReply` | 3 | `app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:343`<br>`app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:387`<br>`app/nimarkogram/messenger/ui/MessageMenuTweaks.java:33` |
| `showReport` | 2 | `app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:220`<br>`app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:464` |
| `showSaveForNotifications` | 1 | `app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:380` |
| `showSaveMessage` | 3 | `app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:161`<br>`app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:457`<br>`org/telegram/ui/ChatActivity.java:32744` |
| `showSaveToDownloads` | 3 | `app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:347`<br>`app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:415`<br>`app/nimarkogram/messenger/ui/MessageMenuTweaks.java:40` |
| `showSaveToGallery` | 3 | `app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:345`<br>`app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:394`<br>`app/nimarkogram/messenger/ui/MessageMenuTweaks.java:37` |
| `showSearchInTabs` | 2 | `app/nimarkogram/messenger/utils/ui/MainTabsManager.kt:196`<br>`app/nimarkogram/messenger/utils/ui/MainTabsManager.kt:68` |
| `showSeconds` | 1 | `com/exteragram/messenger/ExteraConfig.java:123` |
| `showShare` | 3 | `app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:349`<br>`app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:422`<br>`app/nimarkogram/messenger/ui/MessageMenuTweaks.java:43` |
| `showViewHistory` | 2 | `app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:134`<br>`app/nimarkogram/messenger/NimarkoMessageMenuInjector.java:450` |
| `silenceNonContacts` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `sleepTimer` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `sleepTimerAt` | 1 | `app/nimarkogram/messenger/utils/NimarkoSleepTimer.kt:84` |
| `sleepTimerMinutes` | 2 | `app/nimarkogram/messenger/utils/NimarkoSleepTimer.kt:85`<br>`app/nimarkogram/messenger/utils/NimarkoSleepTimer.kt:94` |
| `slider_gifsAmplifier` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `slider_mediaAmplifier` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `slider_stickerAmplifier` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `slowNetworkMode` | 1 | `org/telegram/tgnet/ConnectionsManager.java:744` |
| `sortByUnread` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `springAnimation` | 1 | `app/nimarkogram/messenger/drawer/DrawerContainer.java:625` |
| `startFromUltraWideCam` | 10 | `app/nimarkogram/messenger/camera/CameraXUtils.java:487`<br>`app/nimarkogram/messenger/camera/NimarkoCameraXController.java:1587`<br>`app/nimarkogram/messenger/camera/NimarkoCameraXController.java:1616` |
| `stickerSize` | 1 | `org/telegram/ui/Cells/ChatMessageCell.java:8993` |
| `stickerTimeMode` | 2 | `org/telegram/ui/Cells/ChatMessageCell.java:21301`<br>`org/telegram/ui/Cells/ChatMessageCell.java:21303` |
| `stickersDrawShareButton` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `supergroupsDrawShareButton` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `switchStyle` | 2 | `app/nimarkogram/messenger/appearance/AppearanceConfig.java:39`<br>`org/telegram/ui/Components/Switch.java:237` |
| `systemEmoji` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `systemFonts` | 1 | `com/exteragram/messenger/ExteraConfig.java:135` |
| `tabMode` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `tabStyleStroke` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `tabletMode` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `tabsHideAllChats` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `tabsNoUnread` | 2 | `app/nimarkogram/messenger/utils/folders/NimarkoFoldersHelper.java:129`<br>`app/nimarkogram/messenger/utils/folders/NimarkoFoldersHelper.java:137` |
| `telegramPlusMessageMenu` | 1 | `app/nimarkogram/messenger/ui/MessageMenuTelegramPlus.java:52` |
| `translationKeyboardTarget` | 2 | `app/nimarkogram/messenger/utils/text/BaseTranslator.java:185`<br>`app/nimarkogram/messenger/utils/text/Translator.java:50` |
| `translationTarget` | 4 | `app/nimarkogram/messenger/utils/text/BaseTranslator.java:181`<br>`app/nimarkogram/messenger/utils/text/Translator.java:50`<br>`app/nimarkogram/messenger/utils/text/TranslatorUtils.java:44` |
| `translucentDeletedMessages` | 1 | `org/telegram/ui/Cells/ChatMessageCell.java:17322` |
| `unarchiveOnSwipe` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `unreadBadgeOnBackButton` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `uploadSpeedBoost` | 1 | `org/telegram/messenger/FileUploadOperation.java:56` |
| `useDualCamera` | 2 | `org/telegram/ui/Components/InstantCameraView.java:1182`<br>`org/telegram/ui/Components/InstantCameraView.java:1188` |
| `usersDrawShareButton` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `vibrateInChats` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `videoMessagesAudioBitrateKbps` | 1 | `org/telegram/ui/Components/InstantCameraView.java:4299` |
| `videoMessagesBitrateKbps` | 1 | `org/telegram/ui/Components/InstantCameraView.java:4298` |
| `videoMessagesCamera` | 3 | `org/telegram/ui/Components/ChatActivityEnterView.java:917`<br>`org/telegram/ui/Components/ChatActivityEnterView.java:933`<br>`org/telegram/ui/Components/ChatActivityEnterView.java:934` |
| `videoMessagesHintCount` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `videoMessagesResolution` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `videoSeekDuration` | 1 | `com/exteragram/messenger/ExteraConfig.java:131` |
| `voiceTranscriptionProvider` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `weekdayNearDate` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `wsAutoSubscribed` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
| `wsInstallId` | 0 | Только preferences / accessor / не используется: требуется ручная проверка |
