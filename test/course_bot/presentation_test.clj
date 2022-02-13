(ns course-bot.presentation-test
  (:require [course-bot.presentation :as pres]
            [course-bot.general :as general]
            [codax.core :as codax]
            [course-bot.talk :as talk]
            [clojure.test :refer :all]))

(talk/deftest submit-without-config-talk-test [db *chat]
  (let [submit-talk-no-config (pres/submit-talk db "TOKEN" "noconfig")
        start-talk (general/start-talk db "TOKEN")]
    (submit-talk-no-config (talk/msg "/noconfigsubmit"))
    (is (= "Not registered. Do /start" (-> @*chat first :msg)))

    (testing "register user"
      (start-talk (talk/msg "/start"))
      (start-talk (talk/msg "Bot Botovich"))
      (start-talk (talk/msg "gr1"))
      (start-talk (talk/msg "/start"))
      (is (= "You are already registered, to change your unform the teacher and send /whoami."
             (-> @*chat first :msg))))

    (submit-talk-no-config (talk/msg "/noconfigsubmit"))
    (is (= "You should specify your group for presentation by /noconfigsetgroup"
           (-> @*chat first :msg)))))

(talk/deftest submit-and-check-talk-test [db *chat]
  (let [start-talk (general/start-talk db "TOKEN")
        setgroup-talk (pres/setgroup-talk db "TOKEN" "lab1")
        submit-talk (pres/submit-talk db "TOKEN" "lab1")
        check-talk (pres/check-talk db "TOKEN" "lab1" general/assert-admin)]

    (testing "register user"
      (start-talk (talk/msg "/start"))
      (start-talk (talk/msg "Bot Botovich"))
      (start-talk (talk/msg "gr1"))
      (is (= "Send /help for help." (-> @*chat first :msg))))

    (testing "set presentation group"
      (setgroup-talk (talk/msg "/lab1setgroup"))
      (is (= "Please, select your presentation group: ext" (-> @*chat first :msg)))
      (setgroup-talk (talk/msg "bla-bla"))
      (is (= "I don't know this group. Repeat please (ext):" (-> @*chat first :msg)))
      (setgroup-talk (talk/msg "ext"))
      (is (= "Your presentation group setted: ext" (-> @*chat first :msg)))
      (setgroup-talk (talk/msg "/lab1setgroup"))
      (is (= "Your presentation group: ext" (-> @*chat first :msg))))

    (testing "wrong submit"
      (submit-talk (talk/msg "/lab1submit"))
      (is (= "Please, provide description for your presentation (in one message):"
             (-> @*chat first :msg)))

      (submit-talk (talk/msg "my-presentation"))
      (is (= ["Your description:" "my-presentation" "Do you approve it?"]
             (->> @*chat (take 3) (map :msg) reverse)))

      (submit-talk (talk/msg "bla-bla"))
      (is (= "Please, yes or no?" (-> @*chat first :msg)))

      (submit-talk (talk/msg "no"))
      (is (= "You can do this later." (-> @*chat first :msg))))

    (testing "submit"
      (submit-talk (talk/msg "/lab1submit"))
      (submit-talk (talk/msg "my-presentation"))
      (submit-talk (talk/msg "yes"))
      (is (= "Registered, the teacher will check it soon." (-> @*chat first :msg)))
      (is (= {:description "my-presentation" :on-review? true :group "ext"}
             (codax/get-at! db [1 :pres "lab1"])))

      (submit-talk (talk/msg "/lab1submit"))
      (is (= "On review, you will be informed when it finished." (-> @*chat first :msg))))

    (testing "try to check by student"
      (check-talk (talk/msg "/lab1check"))
      (is (= "That action require admin rights." (-> @*chat first :msg))))

    (testing "check by admin and reject"
      (check-talk (talk/msg 0 "/lab1check"))
      (is (= ["Было пирслано следующее на согласование (группа gr1): \n\nTopic: my-presentation"
              "my-presentation"
              "Approve (yes or no)?"]
             (->> @*chat (take 3) (map :msg) reverse)))

      (check-talk (talk/msg 0 "bla-bla"))
      (is (= "Please, yes or no?" (-> @*chat first :msg)))

      (check-talk (talk/msg 0 "no"))
      (is (= "OK, you need send your remark for the student:" (-> @*chat first :msg)))

      (check-talk (talk/msg 0 "You can do it better!"))
      (is (= [{:id 0 :msg "Presentation description declined. The student was informed about your decision.\n\n/lab1check"}
              {:id 1 :msg "Your presentation description for lab1 declined with the following remark:\n\nYou can do it better!"}]
             (->> @*chat (take 2) reverse)))
      (is (= {:description "my-presentation" :on-review? false :group "ext"}
             (codax/get-at! db [1 :pres "lab1"]))))

    (testing "resubmit presentation"
      (submit-talk (talk/msg "/lab1submit"))
      (submit-talk (talk/msg "my-presentation-2"))
      (submit-talk (talk/msg "yes"))
      (is (= {:description "my-presentation-2" :on-review? true :group "ext"}
             (codax/get-at! db [1 :pres "lab1"])))
      (is (= "Registered, the teacher will check it soon."
             (-> @*chat first :msg))))

    (testing "check by admin and reject"
      (check-talk (talk/msg 0 "/lab1check"))
      (is (= ["Было пирслано следующее на согласование (группа gr1): \n\nTopic: my-presentation-2"
              "my-presentation-2"
              "Approve (yes or no)?"]
             (->> @*chat (take 3) (map :msg) reverse)))

      (check-talk (talk/msg 0 "yes"))
      (is (= [{:id 0 :msg "OK, student will reveive his approve.\n\n/lab1check"}
              {:id 1 :msg "Your presentation description for lab1 approved."}]
             (->> @*chat (take 2) reverse)))
      (is (= {:description "my-presentation-2" :on-review? false :approved? true :group "ext"}
             (codax/get-at! db [1 :pres "lab1"]))))))

(run-tests)
