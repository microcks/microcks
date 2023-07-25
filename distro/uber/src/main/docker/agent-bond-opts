#!/bin/sh

# Parse options
while [ $# -gt 0 ]
do
key="$1"
case ${key} in
    # Escape for scripts which eval the output of this script
    -e|--escape)
    escape_sep=1
    ;;
esac
shift
done

# Check whether a given config is contained in AB_JOLOKIA_OPTS
is_in_jolokia_opts() {
  local prop=$1
  if [ -n "${AB_JOLOKIA_OPTS:-}" ] && [ "${AB_JOLOKIA_OPTS}" != "${AB_JOLOKIA_OPTS/${prop}/}" ]; then
     echo "yes"
  else
     echo "no"
  fi
}

dir=${AB_DIR:-/opt/agent-bond}
sep="="

# Options separators defined to avoid clash with fish-pepper templating
ab_open_del="{""{"
ab_close_del="}""}"
if [ -n "${escape_sep:-}" ]; then
  ab_open_del='\{\{'
  ab_close_del='\}\}'
fi

if [ -z "${AB_OFF:-}" ]; then
   opts="-javaagent:$dir/agent-bond.jar"
   config="${AB_CONFIG:-$dir/agent-bond.properties}"
   if [ -f "$config" ]; then
      # Configuration takes precedence
      opts="${opts}${sep}config=${config}"
      sep=","
   fi
   if [ -z "${AB_ENABLED:-}" ] || [ "${AB_ENABLED}" != "${AB_ENABLED/jolokia/}" ]; then
     # Direct options only if no configuration is found
     jsep=""
     jolokia_opts=""
     if [ -n "${AB_JOLOKIA_CONFIG:-}" ] && [ -f "${AB_JOLOKIA_CONFIG}" ]; then
        jolokia_opts="${jolokia_opts}${jsep}config=${AB_JOLOKIA_CONFIG}"
        jsep=","
        grep -q -e '^host' ${AB_JOLOKIA_CONFIG} && host_in_config=1
     fi
     if [ -z "${AB_JOLOKIA_HOST:-}" ] && [ -z "${host_in_config:-}" ]; then
        AB_JOLOKIA_HOST='0.0.0.0'
     fi
     if [ -n "${AB_JOLOKIA_HOST:-}" ]; then
        jolokia_opts="${jolokia_opts}${jsep}host=${AB_JOLOKIA_HOST}"
        jsep=","
     fi
     if [ -n "${AB_JOLOKIA_PORT:-}" ]; then
        jolokia_opts="${jolokia_opts}${jsep}port=${AB_JOLOKIA_PORT}"
        jsep=","
     fi
     if [ -n "${AB_JOLOKIA_USER:-}" ]; then
        jolokia_opts="${jolokia_opts}${jsep}user=${AB_JOLOKIA_USER}"
        jsep=","
     fi
     if [ -n "${AB_JOLOKIA_PASSWORD:-}" ]; then
        jolokia_opts="${jolokia_opts}${jsep}password=${AB_JOLOKIA_PASSWORD}"
        jsep=","
     fi
     if [ -n "${AB_JOLOKIA_HTTPS:-}" ]; then
        jolokia_opts="${jolokia_opts}${jsep}protocol=https"
        https_used=1
        jsep=","
     fi
     # Integration with OpenShift client cert auth
     if [ -n "${AB_JOLOKIA_AUTH_OPENSHIFT:-}" ]; then
        auth_opts="useSslClientAuthentication=true,extraClientCheck=true"
        if [ -z "${https_used+x}" ]; then
          auth_opts="${auth_opts},protocol=https"
        fi
        if [ $(is_in_jolokia_opts "caCert") != "yes" ]; then
           auth_opts="${auth_opts},caCert=/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"
        fi
        if [ $(is_in_jolokia_opts "clientPrincipal") != "yes" ]; then
           if [  "${AB_JOLOKIA_AUTH_OPENSHIFT}" != "${AB_JOLOKIA_AUTH_OPENSHIFT/=/}" ]; then
              # Supposed to contain a principal name to check
              auth_opts="${auth_opts},clientPrincipal=$(echo ${AB_JOLOKIA_AUTH_OPENSHIFT} | sed -e 's/ /\\\\ /g')"
           else
              auth_opts="${auth_opts},clientPrincipal=cn=system:master-proxy"
           fi
        fi
        jolokia_opts="${jolokia_opts}${jsep}${auth_opts}"
        jsep=","
     fi
     # Add extra opts to the end
     if [ -n "${AB_JOLOKIA_OPTS:-}" ]; then
        jolokia_opts="${jolokia_opts}${jsep}${AB_JOLOKIA_OPTS}"
        jsep=","
     fi

     opts="${opts}${sep}jolokia${ab_open_del}${jolokia_opts}${ab_close_del}"
     sep=","
   fi
   if [ -z "${AB_ENABLED:-}" ] || [ "${AB_ENABLED}" != "${AB_ENABLED/jmx_exporter/}" ]; then
     je_opts=""
     jsep=""
     if [ -n "${AB_JMX_EXPORTER_OPTS:-}" ]; then
        opts="${opts}${sep}jmx_exporter${ab_open_del}${AB_JMX_EXPORTER_OPTS}${ab_close_del}"
        sep=","
     else
        port=${AB_JMX_EXPORTER_PORT:-9779}
        config=${AB_JMX_EXPORTER_CONFIG:-/opt/agent-bond/jmx_exporter_config.yml}
        opts="${opts}${sep}jmx_exporter${ab_open_del}${port}:${config}${ab_close_del}"
        sep=","
     fi
   fi
   if [ "${sep:-}" != '=' ] ; then
     echo ${opts}
   fi
fi