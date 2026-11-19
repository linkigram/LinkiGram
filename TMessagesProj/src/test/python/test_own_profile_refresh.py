"""Хост-контракты: правка своего профиля видна локально сразу.

Баг был в двух местах:
  * SettingsActivity подписывалась на account-события через глобальный
    NotificationCenter, поэтому обложка настроек не получала
    updateInterfaces/mainUserInfoChanged и держала старое имя, @username,
    телефон и аватар;
  * после сохранения обновлялся только текущий пользователь, а
    TLRPC.UserFull.user и строка users в базе оставались со старым именем.

Тесты читают исходники — Android SDK для них не нужен.
"""

import pathlib
import re
import unittest

REPO = pathlib.Path(__file__).resolve().parents[4]
UI = REPO / 'TMessagesProj/src/main/java/org/telegram/ui'


def read(name):
    return (UI / name).read_text(encoding='utf-8')


def method_body(source, marker, end_marker):
    start = source.index(marker)
    end = source.index(end_marker, start)
    return source[start:end]


class SettingsCoverRefreshTest(unittest.TestCase):
    def test_account_events_are_observed_on_account_center(self):
        source = read('SettingsActivity.java')
        self.assertIsNone(
            re.search(
                r'NotificationCenter\.getGlobalInstance\(\)\s*'
                r'\.createObserversGroup\(this\)',
                source,
            ),
            'account-события снова подписаны на глобальный центр',
        )
        group = method_body(
            source,
            'public boolean onFragmentCreate()',
            'private boolean ignoreClearViews;',
        )
        self.assertIn('getNotificationCenter()', group)
        self.assertIn('.createObserversGroup(this)', group)
        for event in (
            'NotificationCenter.updateInterfaces',
            'NotificationCenter.starBalanceUpdated',
            'NotificationCenter.newSuggestionsAvailable',
            'NotificationCenter.mainUserInfoChanged',
        ):
            self.assertIn(event, group)

    def test_cover_refreshes_on_update_and_on_resume(self):
        source = read('SettingsActivity.java')
        handler = method_body(
            source,
            'public void didReceivedNotification(',
            'public void setInfo()',
        )
        self.assertIn('NotificationCenter.updateInterfaces', handler)
        self.assertIn('NotificationCenter.mainUserInfoChanged', handler)
        self.assertIn('setInfo();', handler)

        resume = method_body(
            source,
            'public void onResume()',
            'private void setActionBarVisibilityFactor',
        )
        self.assertIn('setInfo();', resume)


class OwnProfileCacheSyncTest(unittest.TestCase):
    def test_edit_profile_syncs_userfull_and_storage(self):
        source = read('UserInfoActivity.java')
        done = method_body(
            source,
            'private void processDone(boolean error)',
            'public static class AdminedChannelsFetcher',
        )
        cache = done.index('getMessagesController().getUser(selfId)')
        userfull = done.index('userFull.user.first_name = user.first_name;')
        storage = done.index(
            'getMessagesStorage().putUsersAndChats(changedUsers, null, false, true);')
        update = done.rindex('getMessagesStorage().updateUserInfo(userFull, false);')
        self.assertLess(cache, userfull)
        self.assertLess(userfull, storage)
        self.assertLess(storage, update)
        self.assertIn('getMessagesController().putUser(user, false);', done)

    def test_change_name_syncs_canonical_user_and_userfull(self):
        source = read('ChangeNameActivity.java')
        save = method_body(
            source,
            'private void saveName()',
            'public Theme.ResourcesProvider getResourceProvider()',
        )
        self.assertIn(
            'userFull.user.first_name = req.first_name;', save)
        self.assertIn(
            'userFull.user.last_name = req.last_name;', save)
        self.assertIn(
            'MessagesStorage.getInstance(currentAccount)'
            '.putUsersAndChats(users, null, false, true);',
            save,
        )
        self.assertIn(
            'MessagesController.getInstance(currentAccount).putUser(currentUser, false);',
            save,
        )


if __name__ == '__main__':
    unittest.main()