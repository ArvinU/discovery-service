# Push this monorepo to GitHub

You need a GitHub account and either the **GitHub CLI** (`gh`) or **SSH/HTTPS** + a personal access token.

## 1. Create the repository on GitHub

- In the browser: **New repository** → name it (e.g. `discovery-platform`) → create **without** README (this repo already has one).
- Or CLI: `gh repo create YOUR_USER/discovery-platform --public --source=. --remote=origin --push`  
  (run from the repo root after the first commit below).

## 2. Commit locally

```bash
cd /path/to/discovery-service   # repo root (contains pom.xml + example-* folders)

git init
git add .
printf '%s\n' 'Monorepo: discovery server, examples, ProcMan' > .git_commit_msg.txt
git commit -F .git_commit_msg.txt && rm .git_commit_msg.txt
```

## 3. Add remote and push

```bash
git branch -M main
git remote add origin git@github.com:YOUR_USER/discovery-platform.git
# or: https://github.com/YOUR_USER/discovery-platform.git

git push -u origin main
```

If you use HTTPS, GitHub will prompt for credentials; use a **personal access token** as the password.

## 4. Clone elsewhere

```bash
git clone git@github.com:YOUR_USER/discovery-platform.git
cd discovery-platform
./scripts/build-all.sh
```

Replace `YOUR_USER` and `discovery-platform` with your GitHub username and repo name.
