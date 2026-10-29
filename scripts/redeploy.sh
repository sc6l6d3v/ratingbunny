#!/bin/bash
# Replace a running RatingBunny container (rs-svc-dev or rs-svc-prod) with a freshly pulled
# nanothermite/ratingslave:rest-<ENV>. The new container copies the old one's env, network,
# published ports, restart policy, extra hosts and bind mounts, so no per-host flags are needed.
# The running container is left alone unless the pulled image passes its checks.
#
# Usage (as root): redeploy.sh [-n] DEV|PROD [expected jar sha256]
#   -n   dry run: pull and check the image, print the docker run command, change nothing
set -euo pipefail

usage() { echo "usage: $0 [-n] DEV|PROD [expected jar sha256]" >&2; exit 2; }
dry=
[[ ${1:-} == -n ]] && { dry=1; shift; }
case ${1:-} in DEV|PROD) env=$1 ;; *) usage ;; esac
want=${2:-}
name=rs-svc-${env,,}
repo=nanothermite/ratingslave
img=$repo:rest-$env
envf=/root/$name.env

old=$(docker inspect "$name" --format '{{.Image}}')
unsupported=$(docker inspect "$name" --format '{{if index .HostConfig "Mounts"}}mounts {{end}}{{if index .HostConfig "Privileged"}}privileged {{end}}{{if index .HostConfig "CapAdd"}}cap-add {{end}}{{if index .HostConfig "Devices"}}devices {{end}}{{with index .HostConfig "Memory"}}{{if ne (printf "%v" .) "0"}}memory {{end}}{{end}}{{with index .HostConfig "NanoCpus"}}{{if ne (printf "%v" .) "0"}}cpus {{end}}{{end}}{{if index .HostConfig "Links"}}links {{end}}')
[[ -z $unsupported ]] || { echo "$name also uses: $unsupported- this script would drop that, so it stops here" >&2; exit 1; }

docker pull -q "$img" >/dev/null
got=$(docker run --rm --entrypoint sha256sum "$img" /usr/src/data/ratingbunny.jar | cut -d' ' -f1)
echo "pulled $img, jar $got"
[[ -z $want || $got == "$want" ]] || { echo "expected jar $want: not replacing" >&2; exit 1; }
docker run --rm --entrypoint sh "$img" -c \
  '! keytool -list -v -cacerts -storepass changeit 2>/dev/null | grep -E "^Owner:.*(emSign|AAA Certificate Services)"' \
  || { echo "pulled image still trusts emSign/AAA: not replacing" >&2; exit 1; }

mapfile -t flags < <(docker inspect "$name" --format '
--network={{.HostConfig.NetworkMode}}
--restart={{.HostConfig.RestartPolicy.Name}}{{with .HostConfig.RestartPolicy.MaximumRetryCount}}{{if ne (printf "%v" .) "0"}}:{{.}}{{end}}{{end}}
{{range $port, $binds := .HostConfig.PortBindings}}{{range $binds}}--publish={{if .HostIp}}{{.HostIp}}:{{end}}{{.HostPort}}:{{$port}}
{{end}}{{end}}{{range index .HostConfig "ExtraHosts"}}--add-host={{.}}
{{end}}{{range index .HostConfig "Binds"}}--volume={{.}}
{{end}}' | grep .)
echo "new container: docker run -d --name $name --env-file $envf ${flags[*]} $img"
[[ -z $dry ]] || { echo "dry run: $name not changed"; exit 0; }

# The env file keeps only what was passed at run time; the image's own ENV comes from the new image.
umask 077
comm -23 <(docker inspect "$name" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep . | sort) \
         <(docker image inspect "$old" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep . | sort) > "$envf"
echo "saved $(wc -l < "$envf") env vars to $envf"
if [[ $(docker image inspect "$img" --format '{{.Id}}') == "$old" ]]; then
  echo "same image as before: rest-$env-prev left as is"
else
  docker tag "$old" "$repo:rest-$env-prev"
  echo "old image ${old:7:12} tagged rest-$env-prev"
fi
docker stop "$name" >/dev/null && docker rm "$name" >/dev/null
docker run -d --name "$name" --env-file "$envf" "${flags[@]}" "$img" >/dev/null
docker ps --filter "name=^$name\$" --format '{{.Names}} {{.Image}} {{.Status}} {{.Ports}}'
