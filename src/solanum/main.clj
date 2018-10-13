(ns solanum.main
  "Main entry for the daemon."
  (:gen-class)
  (:require
    [clojure.java.shell :as sh]
    [clojure.string :as str]
    [clojure.tools.cli :as cli]
    [clojure.tools.logging :as log]
    [solanum.config :as cfg]
    [solanum.scheduler :as scheduler]))


(defn- load-hostname
  "Look up the name of the local host."
  [ctx]
  (let [result (sh/sh "hostname")]
    (if (zero? (:exit result))
      (str/trim-newline (:out result))
      (log/warn "Failed to resolve local hostname:" (pr-str (:err result))))))


(defn- parse-attr-opt
  "Parse an attribute option and add it to the option map."
  [opts id arg]
  (let [[k v] (str/split arg #"=" 2)]
    (assoc-in opts [id k] v)))


(def cli-options
  "Command-line tool options."
  [["-H" "--host NAME" "Metric event host name"
    :default-fn load-hostname]
   ["-a" "--attribute KEY=VAL" "Attribute to add to every event (may be set multiple times)"
    :default {}
    :default-desc ""
    :assoc-fn parse-attr-opt]
   ["-t" "--tag TAG" "Tag to add to every event (may be set multiple times)"
    :default #{}
    :default-desc ""
    :assoc-fn #(update %1 %2 conj %3)]
   [nil "--ttl SECONDS" "Default TTL for events"
    :parse-fn #(Integer/parseInt %)
    :default 60]
   ["-h" "--help"]])


(defn -main
  "Main entry point."
  [& args]
  (let [parse (cli/parse-opts args cli-options)
        config-paths (parse :arguments)
        options (parse :options)]
    (when-let [errors (parse :errors)]
      (binding [*out* *err*]
        (run! println errors)
        (System/exit 1)))
    (when (or (:help options) (empty? config-paths))
      (println "Usage: solanum [options] <config.yml> [config2.yml ...]")
      (newline)
      (println (parse :summary))
      (flush)
      (System/exit (if (:help options) 0 1)))
    (let [config (->> (map cfg/load-file config-paths)
                      (reduce cfg/merge-config)
                      (cfg/initialize-plugins))]
      (when (empty? (:sources config))
        (binding [*out* *err*]
          (println "No sources defined in configuration files")
          (System/exit 2)))
      (when (empty? (:outputs config))
        (binding [*out* *err*]
          (println "No outputs defined in configuration files")
          (System/exit 2)))
      (println '(do the-thing))
      ; TODO: enter scheduling loop
      ; keep an ordered list of next-runs
      ; sleep until next scheduled source
      ; start a new thread which:
      ;   - collects events from the source
      ;   - puts the events into a queue
      ;   - re-schedules the source for the next period
      (prn config))
    (System/exit 0)))
