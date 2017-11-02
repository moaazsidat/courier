import { Fruits } from "./org.coursera.enums.Fruits";
import { Map } from "./CourierRuntime";
import { CustomInt } from "./org.coursera.customtypes.CustomInt";
import { Simple } from "./org.coursera.records.test.Simple";

export interface WithComplexTypeDefaults {
  
  record : Simple;
  
  "enum" : Fruits;
  
  union : WithComplexTypeDefaults.Union;
  
  array : Array<number>;
  
  map : Map<number>;
  
  custom : CustomInt;
}
export module WithComplexTypeDefaults {
  
  export type Union = Union.IntMember | Union.StringMember | Union.SimpleMember;
  export module Union {
    export interface UnionMember {
      [key: string]: number | string | Simple;
    }
    export interface IntMember extends UnionMember {
      "int": number;
    }
    export interface StringMember extends UnionMember {
      "string": string;
    }
    export interface SimpleMember extends UnionMember {
      "org.coursera.records.test.Simple": Simple;
    }
    export function unpack(union: Union) {
      return {
        int: union["int"] as number,
        string$: union["string"] as string,
        simple: union["org.coursera.records.test.Simple"] as Simple
      };
    }
  }
}
