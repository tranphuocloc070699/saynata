# shellcheck shell=bash
# Disk helpers for the deploy scripts (deploy.sh, web/scripts/deploy-web.sh). Source it, do not run it.
# On 2026-10-24 the 16 GB VPS disk filled up with Docker build cache from every deploy, the backend could not
# start (502) and the deploy failed. These helpers keep the cache small and stop a deploy before the disk is full.
# Volumes (Postgres data) are never touched.

min_free_gb="${DEPLOY_MIN_FREE_GB:-2}"   # a build needs room for its layers; below this we do not start one
keep_cache="${DEPLOY_KEEP_BUILD_CACHE:-1GB}"   # build cache kept for fast builds (Maven deps, base layers)

# Free space in whole GB where Docker keeps its data (usually on /).
free_gb() {
  local dir
  dir="$(docker info -f '{{.DockerRootDir}}' 2>/dev/null || true)"
  [ -d "$dir" ] || dir=/
  df -Pk "$dir" | awk 'NR == 2 { print int($4 / 1024 / 1024) }'
}

# Old build cache and dangling images (old builds of our images). Never fails the deploy.
prune_docker() {
  # Docker 28 renamed --keep-storage to --reserved-space; older versions only know --keep-storage.
  docker builder prune -f --reserved-space "$keep_cache" >/dev/null 2>&1 \
    || docker builder prune -f --keep-storage "$keep_cache" >/dev/null \
    || echo "Warning: docker builder prune failed." >&2
  docker image prune -f >/dev/null || echo "Warning: docker image prune failed." >&2
}

# Stop before building when the disk is almost full. Pruning first frees the usual culprit.
check_free_space() {
  if [ "$(free_gb)" -ge "$min_free_gb" ]; then
    return 0
  fi
  echo "Less than ${min_free_gb} GB free. Pruning old Docker build cache and images first." >&2
  prune_docker
  if [ "$(free_gb)" -lt "$min_free_gb" ]; then
    echo "Not enough disk space to build: under ${min_free_gb} GB free even after pruning. Nothing was restarted." >&2
    echo "See what is big: docker system df; sudo du -xh / --max-depth=2 | sort -h | tail -20 (doc-11, Troubleshooting)." >&2
    df -h / >&2
    exit 1
  fi
}
