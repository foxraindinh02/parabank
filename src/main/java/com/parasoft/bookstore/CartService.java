package com.parasoft.bookstore;

import java.math.*;
import java.util.*;
import java.util.concurrent.*;

import javax.jws.*;

/*
 * Bookstore Web Service implementation
 */
@WebService(
        endpointInterface = "com.parasoft.bookstore.ICartService", 
        serviceName = "Bookstore")
public class CartService implements ICartService {
    /**
     * <DL><DT>Description:</DT><DD>
     * TODO add getStaticCart_Id description
     * </DD>
     * <DT>Date:</DT><DD>Oct 7, 2015</DD>
     * </DL>
     * @return
     * @see com.parasoft.bookstore.CartManager#getStaticCart_Id()
     */
    public int getStaticCart_Id() {
        return cart.getStaticCart_Id();
    }

    private static final long timeoutInMilliseconds = 1200000; // 20 minutes
    private static final Map<Integer, TempBook> addedBookIds = 
        Collections.synchronizedMap(new ConcurrentHashMap<Integer, TempBook>());
    private final CartManager cart = new CartManager();
    private int invocationCounter = 0;

    /*
     * (non-Javadoc)
     * @see com.parasoft.parabank.store.ICart#addItemToCart(int, int, int)
     */
    @Override
    public DisplayOrder addItemToCart(Integer cartId, int itemId, int quantity)
        throws Exception
    {
        if (quantity < 0) {
            throw new Exception("Cannot have an order with negative quantity.");
        }

        Order newOrder = new Order(BookStoreDB.getById(itemId),
                quantity, System.currentTimeMillis());

        if (cartId == null || cartId <= 0) {
            cart.addNewItemToCart(newOrder);
            return new DisplayOrder(newOrder, cart.getStaticCart_Id());
        }

        return new DisplayOrder(cart.addExistingItemToCart(cartId, newOrder), cartId);
    }

    /**
     *
     * @see com.parasoft.parabank.store.ICart#updateItemInCart(int, int, int)
     */
    @Override
    public DisplayOrder updateItemInCart(int cartId, int itemId, int quantity)
        throws Exception
    {
        if (quantity < 0) {
            throw new Exception("Cannot update an order with negative quantity.");
        }
        if (cart.getCartSize() == 0) {
            throw new Exception("Did not update order with cartId " + cartId +
                   ", no orders were submitted.");
        }
        
        return new DisplayOrder(
                cart.updateExistingItem(cartId, itemId, quantity), cartId);
    }
    
    /**
     *
     * @see com.parasoft.parabank.store.ICart#getItemByTitle(java.lang.String)
     */
    @Override
    public Book[] getItemByTitle(String title) throws Exception {
        ++invocationCounter;
        Book[] books = BookStoreDB.getByTitleLike(title != null? title : "No Title");
        for (Book b : books) {
            b.inflatePrice(new BigDecimal(invocationCounter/5));
        }
        return books;
    }

    /**
     *
     * @see com.parasoft.parabank.store.ICart#getItemById(int)
     */
    @Override
    public Book getItemById(int id) throws Exception {
        return BookStoreDB.getById(id);
    }

    /**
     *
     * @see com.parasoft.parabank.store.ICart#addNewItemToInventory(com.parasoft.parabank.store.Book)
     */
    @Override
    public synchronized Book addNewItemToInventory(Book book) throws Exception {
        Book existing = null;
        try {
            existing = getItemById(book.getId());
        } catch (Exception e) {
            TempBook tb = new TempBook(book, System.currentTimeMillis());
            addedBookIds.put(new Integer(book.getId()), tb);
            BookStoreDB.addNewItem(tb);
        }
        if (existing != null) {
            throw new Exception("An item with ID=" + book.getId() +
                    " already exists and it has the title: " +
                    existing.getName());
        }
        return book;
    }

    /**
     * 
     * @see com.parasoft.parabank.store.ICart#submitOrder(int)
     */
    @Override
    public SubmittedOrder submitOrder(int cartId) {
        return new SubmittedOrder(cart.removeOrder(cartId),
                                  System.currentTimeMillis());
    }

    /**
     *
     * @see com.parasoft.parabank.store.ICart#getItemsInCart(int)
     */
    @Override
    public CartManager getItemsInCart(int cartId) throws Exception {
        return new CartManager(cartId);
    }

    public void removeExpiredOrdersAndBooks() {
        synchronized (cart) {
            Collection<List<Order>> list = cart.getCart().values();
            Iterator<List<Order>> iterator = list.iterator();
            while (iterator.hasNext()) {
                Iterator<Order> itr = iterator.next().iterator();
                while (itr.hasNext()) {
                    Order order = itr.next();
                    long difference =
                        System.currentTimeMillis() - order.getTimestamp();
                    if (difference > timeoutInMilliseconds) {
                        itr.remove();
                    }
                }
            }
            cart.removeEmptyMappings();
        }
        synchronized (addedBookIds) {
            Iterator<Map.Entry<Integer, TempBook>> iterator =
                addedBookIds.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, TempBook> entry = iterator.next();
                long difference =
                    System.currentTimeMillis() - entry.getValue().getTimestamp();
                if (difference > timeoutInMilliseconds) {
                    BookStoreDB.clearAddedBooks(entry.getValue());
                    iterator.remove();
                }
            }
        }
    }
}