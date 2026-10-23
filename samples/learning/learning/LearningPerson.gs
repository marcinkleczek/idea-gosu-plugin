package learning

// Open Type Hierarchy here, or use the gutter icon next to Name.
class LearningPerson implements Named {
  private var _name : String

  override property get Name() : String { return _name }
  property set Name(value : String) { _name = value }
}
