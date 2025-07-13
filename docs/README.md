# Minecraft Manhunt Latest ドキュメント

このディレクトリには、Minecraft Manhunt Latestプラグインの公式ドキュメントサイトのソースコードが含まれています。

## 構成

- `index.html` - メインページ（ゲームガイド）
- `style.css` - スタイルシート
- `script.js` - インタラクティブ機能
- `favicon.svg` - サイトアイコン

## ローカルでの確認

```bash
# Python 3を使用
python -m http.server 8000

# または Node.jsを使用
npx http-server
```

ブラウザで `http://localhost:8000` にアクセスしてください。

## GitHub Pagesへのデプロイ

### GitHub Actions使用（推奨）

1. GitHubリポジトリの Settings > Pages に移動
2. Source を "GitHub Actions" に設定
3. Save をクリック
4. docs フォルダに変更をプッシュすると自動的にデプロイされます

**注意**: 初回は手動でPages設定を有効にする必要があります。上記手順に従ってください。

### ブランチから直接デプロイ（代替方法）

1. GitHubリポジトリの Settings > Pages に移動
2. Source を "Deploy from a branch" に設定
3. Branch を "master" (または "main")、フォルダを "/docs" に設定
4. Save をクリック

## 更新方法

1. `docs/` 内のファイルを編集
2. 変更をコミット＆プッシュ
3. GitHub Actionsが自動的にサイトを更新

## ライセンス

このドキュメントはMITライセンスの下で公開されています。