# Saynata web

The website for learners (TASK-102). How it works: `backlog/docs/features/doc-23 - Web-app.md`.

```bash
npm install
npm run dev        # http://localhost:3000, /api goes to https://api.saynata.com
npm test           # Vitest
npm run build
```

`SAYNATA_API_ORIGIN=http://localhost:8081 npm run dev` points `/api` at a local backend.
