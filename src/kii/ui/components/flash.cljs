(ns kii.ui.components.flash
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r]
            [cljs.core.async :refer [chan <! >! put! close!]]
            [taoensso.timbre :as timbre :refer-macros [log logf]]
            [kii.ui.re-frame :refer [<<= <== =>> >=>]]
            [cljs-react-material-ui.core :as mui-core]
            [cljs-react-material-ui.reagent :as mui]
            [kii.ui.mui :as kii-mui]
            [cljs-react-material-ui.icons :as mui-icons]
            [cuerdas.core :as str]
            [cljs-node-io.proc :as proc]
            [kii.util :as util]
            [kii.ui.config :as config]
            [kii.macros :refer-macros [<?]]
            [kii.bindings.npm :refer [command-exists]]
            [kii.bindings.electron-renderer :refer [dialog child-process]]
            [kii.ui.styling :as styling]))

(defn- open-dialog
  [title filters callback]
  (js->clj
    (.showOpenDialog dialog nil
                     (clj->js {:title      title
                               :properties ["openFile"]
                               :filters   filters})
                     callback)))

(defn flash-firmware
  []
  (r/with-let [dfu-path (r/atom (if ((.-sync command-exists) "dfu-util") "dfu-util" ""))
               bin-file (r/atom "")
               flashing? (r/atom false)
               progress (r/atom "")
               status (r/atom :none)]
    ;; TODO - Read this in on start...
    (go
      (if-let [cfg-val (<? (config/get :dfu-util-path))]
        (reset! dfu-path cfg-val)))

    (go
      (if-let [last-dl (<? (config/get :last-download))]
        (reset! bin-file last-dl)))
    
    (add-watch dfu-path :path-update
               (fn [k a prev next]
                 (when (not= prev next)
                   (config/set :dfu-util-path next))))
    (letfn [(exec-flash []
              (reset! progress "")
              (reset! flashing? true)
              (reset! status :in-progress)
              (let [cmd (.spawn child-process @dfu-path #js ["-D" @bin-file])
                    stdout (.-stdout cmd)
                    stderr (.-stderr cmd)]
                (.on cmd "close"
                     (fn [code]
                       (reset! status (if (= code 0) :success :failure))
                       (swap! progress str
                              (if (= code 0)
                                "SUCCESS!"
                                "FAILED!"))))
                (.on stdout "data" (fn [data] (swap! progress str data)))
                (.on stderr "data" (fn [data] (swap! progress str "ERROR: " data)))
                ))]
      [:div
       [:div {:style {:float "left"
                      :width "310px"}}
        [:h3 "Flash Firmware"]
        [:div {:style {:display "flex"}}
         [kii-mui/text-field
          {:floating-label-text "dfu-util command"
           :value               @dfu-path
           :on-change           (fn [_ val] (reset! dfu-path val))
           :error-text          (when-not (or (= 0 (count @dfu-path)) (str/includes? @dfu-path "dfu-util"))
                                  "does not appear to be the dfu-util binary")
           :error-style         {:color (mui-core/color :deep-orange300)}
           }
          ]

         [mui/icon-button
          {:style    {:margin-left "1em" :align-self "flex-end"}
           :on-click #(open-dialog "path to dfu-util" [{:name "All Files" :extensions ["*"]}]
                                   (fn [selected]
                                     (when-let [file (first selected)]
                                       (reset! dfu-path file))))

           }
          [mui-icons/navigation-more-vert
           {:color "black"}]
          ]
         ]

        [:div {:style {:display "flex"}}
         [kii-mui/text-field
          {:floating-label-text ".bin file to flash"
           :value               @bin-file
           :on-change           (fn [_ val] (reset! bin-file val))
           :error-text           (when-not (or (= 0 (count @bin-file)) (str/ends-with? @bin-file ".bin"))
                                  "does not appear to be a .bin file")
           :error-style         {:color (mui-core/color :deep-orange300)}
           }

          ]

         [mui/icon-button
          {:style    {:margin-left "1em" :align-self "flex-end"}
           :on-click #(open-dialog "firmware to flash" [{:name "bin files" :extensions ["bin"]}]
                                   (fn [selected]
                                     (when-let [file (first selected)]
                                       (reset! bin-file file))))

           }
          [mui-icons/navigation-more-vert
           {:color "black"}]
          ]
         ]
        [mui/raised-button
         {:style    {:float "right" :margin-right "55px" :margin-top "20px"}
          :label    "Flash"
          :primary  true
          :disabled (or (str/empty-or-nil? @dfu-path) (str/empty-or-nil? @bin-file) (= @status :in-progress))
          :on-click #(exec-flash)}]
        ]
       (when @flashing?
         [:div {:style {                                    ;:margin-right "320px"
                        :display      "inline-block"}}
          [mui/text-field
           {:value                @progress
            :floating-label-fixed true
            :floating-label-text  "flashing progress"
            :disabled             true
            :multi-line           true
            :rows                 20
            :style                {:display     "block"
                                   :width       "900px"
                                   :font-family styling/monospace-font-stack
                                   :border-top  (str/fmt "10px solid %s" (case @status :success "green" :failure "red" "lightgray"))}
            :textarea-style       {:white-space    "pre"
                                   :padding-bottom "1.2em"
                                   :overflow-y     "hidden"
                                   :color          "black"
                                   :font-size      "0.9em"}}
           ]
          ])
       ])
    )
 )