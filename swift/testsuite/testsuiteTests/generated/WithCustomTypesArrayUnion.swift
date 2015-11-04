import Foundation
import SwiftyJSON

public enum WithCustomTypesArrayUnion: JSONSerializable, Equatable {
    
    case IntMember(Int)
    
    case StringMember(String)
    
    case SimpleMember(Simple)
    case UNKNOWN$([String : JSON])
    
    public static func read(json: JSON) -> WithCustomTypesArrayUnion {
        let dictionary = json.dictionaryValue
        if let member = dictionary["int"] {
            return .IntMember(member.intValue)
        }
        if let member = dictionary["string"] {
            return .StringMember(member.stringValue)
        }
        if let member = dictionary["org.coursera.records.test.Simple"] {
            return .SimpleMember(Simple.read(member.jsonValue))
        }
        return .UNKNOWN$(dictionary)
    }
    public func write() -> JSON {
        switch self {
        case .IntMember(let member):
            return JSON(["int": JSON(member)]);
        case .StringMember(let member):
            return JSON(["string": JSON(member)]);
        case .SimpleMember(let member):
            return JSON(["org.coursera.records.test.Simple": member.write()]);
        case .UNKNOWN$(let dictionary):
            return JSON(dictionary)
        }
    }
}

public func ==(lhs: WithCustomTypesArrayUnion, rhs: WithCustomTypesArrayUnion) -> Bool {
    switch (lhs, rhs) {
    case (let .IntMember(lhs), let .IntMember(rhs)):
        return lhs == rhs
    case (let .StringMember(lhs), let .StringMember(rhs)):
        return lhs == rhs
    case (let .SimpleMember(lhs), let .SimpleMember(rhs)):
        return lhs == rhs
    case (let .UNKNOWN$(lhs), let .UNKNOWN$(rhs)):
        return lhs == rhs
    default:
        return false
    }
}