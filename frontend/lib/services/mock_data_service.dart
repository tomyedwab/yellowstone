import '../models/item.dart';

class MockDataService {
  List<Item> getItems() {
    return [
      Item(
        id: 1,
        title: 'First Item',
        description: 'This is the first item in our list',
      ),
      Item(
        id: 2,
        title: 'Second Item',
        description: 'This is the second item in our list',
      ),
      Item(
        id: 3,
        title: 'Third Item',
        description: 'This is the third item in our list',
      ),
    ];
  }
}
