# Как выпустить новую версию TaskMGR

1. Добавь запись в `CHANGELOG.md`** (сверху, под `# Changelog`):
   ```
   ## [1.0.8] - 2026-11-15
   ### Added
   - Что добавил
   ### Fixed
   - Что починил
   ```

2. Подними версию в app/build.gradle
versionCode 8          // всегда +1 от предыдущего
versionName "1.0.8"    // то, что увидит пользователь

3. Коммиты и пуши
git add .
git commit -m "Release v1.0.8"
git push origin master         

git tag v1.0.8
git push origin v1.0.8