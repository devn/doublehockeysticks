(ns ^{:doc "Helpers for extracting values from messages"}
  com.shayban.hl7v2.api
  (:require com.shayban.hl7v2.parse)
  (:import com.shayban.hl7v2.parse.RepeatingField))
 
;; TODO Use cgrand/regex to extract segments

(defn structure
  "Returns a seq of the segment headers."
  [m]
  (map :id (:segments m)))

(defn segment-name-pred
  "Makes a predicate that matches a segment type"
  [name]
  (fn [seg]
    (= (:id seg) name)))

(defn all-segments
  "Retrieves a seq of all segments matching segment-id"
  [msg segment-id]
  (filter (segment-name-pred segment-id) (:segments msg)))

(defn segment
  "Retrieves only the first matching segment-id."
  [msg segment-id]
  (-> msg
    (all-segments segment-id)
    first))

(defn field
  "Takes a segment and returns a field from it, or nil if it field
   doesn't exist. N.B. field-num is a one-based index.  If the field
   is repeating, this returns the first repetition only."
  [segment field-num]
  (let [idx (dec field-num)]
    (if (< idx (count (:fields segment)))
      (let [fld (nth (:fields segment) idx)]
        (if (:fields fld)
          (first (:fields fld))
          fld)))))

(defn repeating-field
  "Retrieves a seq of fields from a special repeating field from a segment.
   N.B., field-num is a one-based field index. If the field didn't repeat,
   returns a seq of the simple field."
  [segment field-num]
  (let [idx (dec field-num)]
    (if (< idx (count (:fields segment)))
      (let [fld (nth (:fields segment) idx)]
        (or (:fields fld) [fld])))))

(defn component
  "Indexes (0-based) into a field. Doesn't throw for out of bounds index."
  ([field idx]
    (cond
      (and (vector? field) (< idx (count field)))
      (nth field idx)
      (= idx 0)
      field))
  ([field idx sub-idx]
    (let [subcomps (component field idx)]
      (cond
        (vector? subcomps)
        (if (< sub-idx (count subcomps))
          (nth subcomps sub-idx))
        (= 0 sub-idx)
        subcomps
        :else
        (throw (Exception. "Unknown field structure"))))))

(defn field-seq
  "This takes a segment and unrolls into list of maps of the form
   {:segment \"PID\" :field 3 :value \"123\"}, handling repeating fields properly.
   You probably don't want to call this directly except for source feed
   analysis, as it is space inefficient."
   [seg]
   (let [seg-id (:id seg)
         {compound true simple false} (group-by #(instance? RepeatingField (second %))
                                                (map list (iterate inc 1) (:fields seg)))
         all-fields (concat simple
                      (for [[n {fld :fields}] compound] [n fld]))]
       (for [[field-num field-value] all-fields :when field-value]
         {:segment seg-id
          :field field-num
          :value field-value})))


