(ns territory-bro.layout
  (:require [selmer.parser :as parser]
            [selmer.filters :as filters]
            [markdown.core :refer [md-to-html-string]]
            [ring.util.http-response :refer [content-type ok]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [environ.core :refer [env]]))

(declare ^:dynamic *app-context*)
(parser/set-resource-path! (clojure.java.io/resource "templates"))
(parser/add-tag! :csrf-field (fn [_ _] (anti-forgery-field)))
(filters/add-filter! :markdown (fn [content] [:safe (md-to-html-string content)]))
(filters/add-filter! :semicolon-to-newline (fn [text] (clojure.string/replace text ";" "\n")))

(def available-languages #{"en" "fi" "pt"})
(def default-language "en")
(defn current-language [request]
  (or (-> request :params :lang available-languages)
      default-language))

(defn render
  "renders the HTML template located relative to resources/templates"
  [template request & [params]]
  (content-type
    (ok
      (parser/render-file
        template
        (assoc params
          :page template
          :lang (current-language request)
          :uri (:uri request)
          :dev (env :dev)
          :csrf-token *anti-forgery-token*
          :servlet-context *app-context*)))
    "text/html; charset=utf-8"))

(defn error-page
  "error-details should be a map containing the following keys:
   :status - error status
   :title - error title (optional)
   :message - detailed error message (optional)

   returns a response map with the error page as the body
   and the status specified by the status key"
  [error-details]
  {:status  (:status error-details)
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (parser/render-file "error.html" error-details)})
