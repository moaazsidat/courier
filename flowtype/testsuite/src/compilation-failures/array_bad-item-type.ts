import {WithPrimitivesArray} from "./tslite-bindings/org.coursera.arrays.WithPrimitivesArray";

const a: WithPrimitivesArray = {
  "bytes" : [ "\u0000\u0001\u0002",
    "\u0003\u0004\u0005" ],
  "longs" : [ 10, 20, 30 ],
  "strings" : [ "a", "b", "c" ],
  "doubles" : [ 11.1, 22.2, 33.3 ],
  "booleans" : [ false, true ],
  "floats" : [ 1.1, 2.2, 3.3 ],
  "ints" : [ "1", "2", "3" ] // oops! these should be numbers
};
