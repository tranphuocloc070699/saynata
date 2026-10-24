# The web app image. It does NOT build the app: GitHub Actions runs `next build` (a small VPS can run out of memory
# doing it) and copies the result into web/dist on the server. This file only puts that folder into a Node image.
# Guide: backlog/docs/guides/doc-11 - Deploy-to-a-VPS.md, section 14.
FROM node:22-alpine

ENV NODE_ENV=production \
    PORT=3000 \
    HOSTNAME=0.0.0.0 \
    NEXT_TELEMETRY_DISABLED=1

WORKDIR /app
# dist = .next/standalone + .next/static + public (see .github/workflows/deploy.yml, job deploy-web).
COPY --chown=node:node dist/ ./

USER node
EXPOSE 3000
CMD ["node", "server.js"]
